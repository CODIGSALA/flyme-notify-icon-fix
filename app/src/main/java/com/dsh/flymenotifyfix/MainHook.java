package com.dsh.flymenotifyfix;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.Map;

/**
 * Flyme 通知图标扩展模块 v2
 *
 * 原理：
 * 1. 模块自带 fankes 680 个黑白图标（res/drawable-xxhdpi/mz_stat_sys_<包名>.png）
 * 2. hook FlymeNotificationIconUtils.resetNotificationSmallIconIfNeed（before）：
 *    - 包名在系统白名单 Map（mCustomizedIconResIdMap）→ 不动（保持系统原生图标与反色）
 *    - 不在白名单 → addAssetPath 模块 APK → getIdentifier 查模块的 mz_stat_sys_<包名> 资源
 *      → 有则 setSmallIcon(Icon.createWithResource)（RESOURCE 类型，Flyme 渲染有效）
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "FlymeNotifyFix";
    private static final String MODULE_PKG = "com.dsh.flymenotifyfix";

    private static boolean assetAdded = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.flyme.notification.utils.FlymeNotificationIconUtils",
                    lpparam.classLoader,
                    "resetNotificationSmallIconIfNeed",
                    StatusBarNotification.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object self = param.thisObject;
                                StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                                if (sbn == null) return;
                                String pkg = sbn.getOrigPackageName();
                                if (pkg == null) return;

                                Map<String, Integer> map = (Map<String, Integer>) XposedHelpers.getObjectField(self, "mCustomizedIconResIdMap");
                                if (map.containsKey(pkg)) {
                                    // 系统已适配，不动（保持原生图标与反色）
                                    return;
                                }

                                Context ctx = (Context) XposedHelpers.getObjectField(self, "mContext");
                                ensureAssets(ctx);

                                Resources res = ctx.getResources();
                                int resId = res.getIdentifier(
                                        "mz_stat_sys_" + pkg.replace('.', '_'),
                                        "drawable", MODULE_PKG);
                                if (resId != 0) {
                                    Icon newIcon = Icon.createWithResource(ctx, resId);
                                    sbn.getNotification().setSmallIcon(newIcon);
                                    XposedBridge.log(TAG + ": 替换 " + pkg + " -> resId=" + resId);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": 处理异常 " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hook 注册成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 注册失败 " + t);
        }
    }

    private static void ensureAssets(Context ctx) {
        if (assetAdded) return;
        try {
            ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(MODULE_PKG, 0);
            String apkPath = appInfo.sourceDir;
            AssetManager am = ctx.getAssets();
            am.addAssetPath(apkPath);
            assetAdded = true;
            XposedBridge.log(TAG + ": addAssetPath " + apkPath);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addAssetPath 失败 " + t);
        }
    }
}
