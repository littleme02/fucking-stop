package dev.natmon.fuckingstop;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Runs inside each app in the LSPosed scope. Apps swap their launcher icon by
 * enabling one activity-alias and disabling the rest; this drops those calls
 * for the app's own launcher components and, on start, puts them back to the
 * manifest defaults.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "FuckingStop: ";

    private String pkg;
    private Set<String> launchers;
    private Method setOne;

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!lp.isFirstApplication || "android".equals(lp.packageName)) return;
        pkg = lp.packageName;

        Class<?> apm = XposedHelpers.findClass("android.app.ApplicationPackageManager", lp.classLoader);

        setOne = XposedHelpers.findMethodExact(apm, "setComponentEnabledSetting",
                ComponentName.class, int.class, int.class);
        XposedBridge.hookMethod(setOne, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ComponentName c = (ComponentName) param.args[0];
                if (isLauncher((PackageManager) param.thisObject, c)) {
                    XposedBridge.log(TAG + "blocked " + c.flattenToShortString() + " -> " + param.args[1]);
                    param.setResult(null);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            Method setMany = XposedHelpers.findMethodExact(apm, "setComponentEnabledSettings", List.class);
            XposedBridge.hookMethod(setMany, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    PackageManager pm = (PackageManager) param.thisObject;
                    List<?> in = (List<?>) param.args[0];
                    List<Object> kept = new ArrayList<>();
                    int blocked = 0;
                    for (Object o : in) {
                        PackageManager.ComponentEnabledSetting s = (PackageManager.ComponentEnabledSetting) o;
                        if (s.getComponentName() != null && isLauncher(pm, s.getComponentName())) blocked++;
                        else kept.add(o);
                    }
                    if (blocked == 0) return;
                    XposedBridge.log(TAG + "blocked " + blocked + " launcher change(s) in " + pkg);
                    if (kept.isEmpty()) param.setResult(null);
                    else param.args[0] = kept;
                }
            });
        }

        if (pkg.equals(lp.processName)) {
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate",
                    Application.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                reset(((Application) param.args[0]).getPackageManager());
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "reset failed in " + pkg + ": " + t);
                            }
                        }
                    });
        }
    }

    private synchronized Set<String> launchers(PackageManager pm) {
        if (launchers == null) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
            Set<String> names = new HashSet<>();
            for (ResolveInfo r : pm.queryIntentActivities(i, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                names.add(r.activityInfo.name);
            }
            launchers = names;
        }
        return launchers;
    }

    private boolean isLauncher(PackageManager pm, ComponentName c) {
        return pkg.equals(c.getPackageName()) && launchers(pm).contains(c.getClassName());
    }

    /** Puts every launcher component back to its manifest state, if that leaves an icon. */
    private void reset(PackageManager pm) throws Exception {
        Set<String> names = launchers(pm);
        if (names.isEmpty()) return;

        // ComponentInfo.enabled from an installed package can reflect the
        // current state, so read the manifest values from the APK itself.
        String apk = pm.getApplicationInfo(pkg, 0).sourceDir;
        PackageInfo manifest = pm.getPackageArchiveInfo(apk,
                PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
        boolean anyDefault = false;
        if (manifest != null && manifest.activities != null) {
            for (ActivityInfo a : manifest.activities) {
                if (a.enabled && names.contains(a.name)) anyDefault = true;
            }
        }
        if (!anyDefault) {
            XposedBridge.log(TAG + pkg + " has no launcher enabled by default, leaving it alone");
            return;
        }

        List<ComponentName> changed = new ArrayList<>();
        for (String n : names) {
            ComponentName c = new ComponentName(pkg, n);
            if (pm.getComponentEnabledSetting(c) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) changed.add(c);
        }
        if (changed.isEmpty()) return;

        for (ComponentName c : changed) {
            XposedBridge.invokeOriginalMethod(setOne, pm, new Object[]{
                    c, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP});
        }
        XposedBridge.log(TAG + "reset " + changed.size() + " launcher component(s) in " + pkg);
    }
}
