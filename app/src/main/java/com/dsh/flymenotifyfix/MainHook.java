package com.dsh.flymenotifyfix;

import android.content.Context;
import android.content.res.Resources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flyme 通知图标扩展模块
 *
 * 原理：hook FlymeNotificationIconUtils.initIconMap，在系统构建完
 * mCustomizedIconResIdMap（白名单 Map）后，把 fankes 图标库的包名
 * 动态加入 Map（资源 ID 来自主题资源的 mz_stat_sys_<包名>）。
 *
 * 效果：
 * - 白名单内（系统已适配）的 App 不动，保持系统原生图标与反色；
 * - 白名单外的 App 走系统 mz_stat_sys 资源渲染（黑白 + 反色正常）。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "FlymeNotifyFix";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.flyme.notification.utils.FlymeNotificationIconUtils",
                    lpparam.classLoader,
                    "initIconMap",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object self = param.thisObject;
                                Context ctx = (Context) XposedHelpers.getObjectField(self, "mContext");
                                Map<String, Integer> map =
                                        (Map<String, Integer>) XposedHelpers.getObjectField(self, "mCustomizedIconResIdMap");

                                String[] pkgs = loadPackages(ctx);
                                if (pkgs == null) {
                                    XposedBridge.log(TAG + ": 包名列表加载失败");
                                    return;
                                }

                                Resources res = ctx.getResources();
                                int added = 0;
                                int skipped = 0;
                                for (String pkg : pkgs) {
                                    if (map.containsKey(pkg)) {
                                        skipped++; // 已适配的跳过，不动系统图标
                                        continue;
                                    }
                                    int resId = res.getIdentifier(
                                            "mz_stat_sys_" + pkg.replace('.', '_'),
                                            "drawable", "com.android.systemui");
                                    if (resId != 0) {
                                        map.put(pkg, resId);
                                        added++;
                                    }
                                }
                                XposedBridge.log(TAG + ": 扩展完成 新增=" + added + " 已适配跳过=" + skipped);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": hook 异常 " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hook 注册成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 注册失败 " + t);
        }
    }

    private String[] loadPackages(Context ctx) {
        try {
            InputStream is = ctx.getAssets().open("fankes_pkgs.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            List<String> list = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().length() > 0) {
                    list.add(line.trim());
                }
            }
            br.close();
            return list.toArray(new String[0]);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 读取包名列表失败 " + t);
            return null;
        }
    }
}
