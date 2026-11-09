package com.minibar.port;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainHook extends XposedModule {
    private static final String TAG = "MiniBarPort";
    private static final String SYSUI = "com.android.systemui";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Map<Resources, int[]> ID_CACHE = new WeakHashMap<Resources, int[]>();
    private static volatile Resources sLastRes;
    private static volatile int[] sLastIds;
    private static volatile int sMiniBarId;
    private static volatile int sActionRootId;
    private static final ThreadLocal<Boolean> IN_VISIBILITY = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!SYSUI.equals(param.getPackageName())) {
            return;
        }
        Prefs.attach(getRemotePreferences(Prefs.FILE));
        log(Log.INFO, TAG, "onPackageLoaded: " + param.getPackageName());

        boolean hide = Prefs.hide();
        // 隐藏开启时不需要 inflate/parseInclude（宽高和动作区首次回调只在隐藏关闭时用），
        // 隐藏关闭时也不需要 setVisibility 拦截。按开关只装必要的那一组。
        if (!hide) {
            try {
                hookLayoutInflater();
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hookLayoutInflater", t);
            }
        }
        if (hide) {
            try {
                hookHideTargets(param.getDefaultClassLoader());
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hookHideTargets", t);
            }
        }
        if (hide) {
            try {
                hookVisibility();
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hookVisibility", t);
            }
        }
        log(Log.INFO, TAG, "hooks installed, hide=" + hide);
    }

    /** 退场时系统会把 miniBar / 动作区重新设成 VISIBLE，这里拦住。 */
    private void hookVisibility() throws Exception {
        Method setVisibility = View.class.getDeclaredMethod("setVisibility", int.class);
        hookMethod(setVisibility, chain -> {
            Object result = chain.proceed();
            try {
                Object self = chain.getThisObject();
                Object arg = chain.getArg(0);
                if (!(self instanceof View) || !(arg instanceof Integer)) {
                    return result;
                }
                View view = (View) self;
                int id = view.getId();
                int miniBarId = sMiniBarId;
                if (miniBarId == 0) {
                    int[] resolved = ids(view.getResources());
                    miniBarId = resolved[2];
                }
                if (id != miniBarId && id != sActionRootId) {
                    return result;
                }
                if (!Prefs.hide()) {
                    return result;
                }
                int visibility = (Integer) arg;
                int[] ids = sLastIds;
                if (ids == null || sMiniBarId != miniBarId) {
                    ids = ids(view.getResources());
                }
                if (id == miniBarId && visibility != View.GONE && !IN_VISIBILITY.get()) {
                    IN_VISIBILITY.set(true);
                    try {
                        view.setVisibility(View.GONE);
                    } finally {
                        IN_VISIBILITY.set(false);
                    }
                } else if (id == sActionRootId && visibility == View.VISIBLE && !IN_VISIBILITY.get()) {
                    View actions = findById(view, ids[4]);
                    int actionChildCount = actions instanceof ViewGroup
                            ? ((ViewGroup) actions).getChildCount() : 0;
                    if (actions == null || actionChildCount == 0) {
                        IN_VISIBILITY.set(true);
                        try {
                            view.setVisibility(View.GONE);
                        } finally {
                            IN_VISIBILITY.set(false);
                        }
                    }
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "visibility override", t);
            }
            return result;
        });
    }

    // ------------------------------------------------------------ layout

    private void hookLayoutInflater() throws Exception {
        // 1. 普通 inflate：LayoutInflater.inflate(int, ViewGroup, boolean)
        Method inflate = LayoutInflater.class.getDeclaredMethod(
                "inflate", int.class, ViewGroup.class, boolean.class);
        hookMethod(inflate, chain -> {
            Object result = chain.proceed();
            if (result instanceof View) {
                dispatch((View) result, (Integer) chain.getArg(0));
            }
            return result;
        });

        // 2. parseInclude：XML <include> 走这里，旧项目没 Hook 会漏动作区回调。
        // 不再额外 Hook createViewFromTag（它在每次 View 创建都会触发，开销高），
        // 直接取 include 完成后父容器新增的最后一个子 View。
        Method parseInclude = LayoutInflater.class.getDeclaredMethod(
                "parseInclude", XmlPullParser.class, Context.class, View.class, AttributeSet.class);
        hookMethod(parseInclude, chain -> {
            AttributeSet attrs = (AttributeSet) chain.getArg(3);
            int layoutId = attrs.getAttributeResourceValue(ANDROID_NS, "layout", 0);
            View parent = (View) chain.getArg(2);
            Object result = chain.proceed();
            try {
                if (layoutId != 0 && parent instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) parent;
                    if (group.getChildCount() > 0) {
                        dispatch(group.getChildAt(group.getChildCount() - 1), layoutId);
                    }
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "parseInclude dispatch", t);
            }
            return result;
        });
    }

    private void dispatch(View root, int layoutId) {
        try {
            int[] ids = ids(root.getResources());
            if (layoutId == ids[0]) {
                onHeadsUpInflated(root, ids);
            } else if (layoutId == ids[1]) {
                onActionListInflated(root, ids);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "dispatch layout callback", t);
        }
    }

    private void onHeadsUpInflated(View root, int[] ids) {
        // OShape 隐藏分支不在 inflate 阶段处理 heads_up_mini_window_bar。
        if (Prefs.hide()) {
            return;
        }
        View bar = findById(root, ids[2]);
        if (bar == null) {
            return;
        }
        ViewGroup.LayoutParams lp = bar.getLayoutParams();
        if (Prefs.widthEnabled()) {
            lp.width = dp2px(bar, Prefs.width());
        }
        if (Prefs.heightEnabled()) {
            lp.height = dp2px(bar, Prefs.height());
        }
        bar.setLayoutParams(lp);
    }

    private void onActionListInflated(View root, final int[] ids) {
        if (!Prefs.hide()) {
            return;
        }
        final View container = findById(root, ids[3]);
        if (container == null) {
            log(Log.WARN, TAG, "actions_container_layout not found");
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                View actions = findById(container, ids[4]);
                ViewGroup.LayoutParams lp = container.getLayoutParams();
                if (!(lp instanceof FrameLayout.LayoutParams)) {
                    return;
                }
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                boolean actionsVisible = actions != null && actions.getVisibility() == View.VISIBLE;
                flp.bottomMargin = actionsVisible ? dp2px(container, 15f) : 0;
                container.setLayoutParams(flp);
                container.requestLayout();
            }
        };
        if (container.isAttachedToWindow()) {
            container.post(task);
        } else {
            container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    v.post(task);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }
    }

    // ------------------------------------------------------------ space

    private void clearRowSpace(View row) {
        applyRowSpace(row);
        row.requestLayout();
    }

    /** 只处理动作区：OShape 唯一动过的边距就是这里。 */
    private void applyRowSpace(View row) {
        if (!Prefs.hide()) {
            return;
        }
        int[] ids = ids(row.getResources());
        View actionRoot = findById(row, ids[5]);
        View actionContainer = findById(row, ids[3]);
        View actions = findById(row, ids[4]);
        int actionChildCount = actions instanceof ViewGroup
                ? ((ViewGroup) actions).getChildCount() : 0;
        boolean hasActions = actionChildCount > 0;
        if (actionRoot != null) {
            actionRoot.setVisibility(hasActions ? View.VISIBLE : View.GONE);
        }
        if (actionContainer != null) {
            ViewGroup.LayoutParams lp = actionContainer.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.bottomMargin = hasActions ? dp2px(actionContainer, 15f) : 0;
            }
        }
    }

    // ------------------------------------------------------------ hide

    private void hookHideTargets(ClassLoader cl) {
        hookIfPresent(cl,
                "com.miui.systemui.notification.NotificationSettingsManager",
                "updateMiniBar",
                chain -> {
                    Object result = chain.proceed();
                    hideView(getField(chain.getThisObject(), "miniBar"));
                    return result;
                });
        hookIfPresent(cl,
                "com.android.systemui.shade.MiuiExpandableNotificationRow",
                "updateMiniWindowBar",
                chain -> {
                    Object result = chain.proceed();
                    hideView(callMethod(chain.getThisObject(), "getMMiniBar"));
                    return result;
                });
        hookIfPresent(cl,
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector",
                "updateMiniWindowBar",
                chain -> {
                    Object result = chain.proceed();
                    hideView(callMethod(chain.getThisObject(), "getMiniBar"));
                    return result;
                });
    }

    private void hookIfPresent(ClassLoader cl, String className, String methodName,
                                XposedInterface.Hooker hooker) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, cl);
        } catch (Throwable t) {
            log(Log.INFO, TAG, "class not found: " + className);
            return;
        }
        int count = 0;
        Class<?> c = clazz;
        while (c != null) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                try {
                    hookMethod(method, hooker);
                    count++;
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "hook " + method, t);
                }
            }
            c = c.getSuperclass();
        }
        log(Log.INFO, TAG, "hooked " + className + "#" + methodName + " x" + count);
    }

    private void hideView(Object object) {
        if (!(object instanceof View)) {
            return;
        }
        if (!Prefs.hide()) {
            return;
        }
        View miniBar = (View) object;
        miniBar.setVisibility(View.GONE);
        clearRowSpace(miniBar);
    }

    private static int[] ids(Resources res) {
        int[] cached = sLastIds;
        if (cached != null && sLastRes == res) {
            return cached;
        }
        synchronized (ID_CACHE) {
            cached = ID_CACHE.get(res);
            if (cached != null) {
                sLastRes = res;
                sLastIds = cached;
                return cached;
            }
            int[] ids = new int[]{
                res.getIdentifier("heads_up_mini_window_bar", "layout", SYSUI),
                res.getIdentifier("notification_material_action_list", "layout", SYSUI),
                res.getIdentifier("mini_window_bar", "id", SYSUI),
                res.getIdentifier("actions_container_layout", "id", SYSUI),
                res.getIdentifier("actions", "id", SYSUI),
                    res.getIdentifier("actions_container", "id", SYSUI)
            };
            ID_CACHE.put(res, ids);
            sLastRes = res;
            sLastIds = ids;
            sMiniBarId = ids[2];
            sActionRootId = ids[5];
            return ids;
        }
    }

    private void hookMethod(Executable executable, XposedInterface.Hooker hooker) {
        hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker);
    }

    private static View findById(View root, int id) {
        if (id == 0) {
            return null;
        }
        if (root.getId() == id) {
            return root;
        }
        return root.findViewById(id);
    }

    private static int dp2px(View view, float dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    private static Object getField(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        String key = object.getClass().getName() + "#" + fieldName;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            try {
                return cached.get(object);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        Class<?> c = object.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                FIELD_CACHE.put(key, f);
                return f.get(object);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private static Object callMethod(Object object, String methodName) {
        if (object == null) {
            return null;
        }
        String key = object.getClass().getName() + "#" + methodName;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            try {
                return cached.invoke(object);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        Class<?> c = object.getClass();
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(methodName);
                m.setAccessible(true);
                METHOD_CACHE.put(key, m);
                return m.invoke(object);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }
}
