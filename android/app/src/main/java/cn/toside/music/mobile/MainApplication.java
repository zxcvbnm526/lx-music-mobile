package cn.toside.music.mobile;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Environment;
import android.os.StatFs;
import java.io.File;

import com.facebook.react.PackageList;
import com.facebook.react.flipper.ReactNativeFlipper;
import com.reactnativenavigation.NavigationApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.reactnativenavigation.react.NavigationReactNativeHost;
import java.util.List;

import cn.toside.music.mobile.cache.CachePackage;
import cn.toside.music.mobile.crypto.CryptoPackage;
import cn.toside.music.mobile.lyric.LyricPackage;
import cn.toside.music.mobile.userApi.UserApiPackage;
import cn.toside.music.mobile.utils.UtilsPackage;

public class MainApplication extends NavigationApplication {

  // --- 全局拦截底层存储路径，按容量识别目标固态硬盘 ---
  @Override
  protected void attachBaseContext(Context base) {
    File[] externalDirs = base.getExternalFilesDirs(null);
    File targetSsdDir = null;
    
    // 设定判定阈值为 400 GiB 的字节数
    final long THRESHOLD_BYTES = 400L * 1024 * 1024 * 1024;

    if (externalDirs != null) {
      for (File dir : externalDirs) {
        // 判定条件1：必须是可移除的物理设备
        if (dir != null && Environment.isExternalStorageRemovable(dir)) {
          try {
            // 判定条件2：读取文件系统的底层容量块数据
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            // Android 4.3 (API 18) 引入了 getTotalBytes()
            long totalBytes = statFs.getTotalBytes();
            
            if (totalBytes > THRESHOLD_BYTES) {
              targetSsdDir = dir;
              break; // 准确命中 512GB 固态硬盘，终止遍历
            }
          } catch (IllegalArgumentException e) {
            // 忽略未挂载成功或无访问权限的异常路径，继续遍历下一个设备
          }
        }
      }
    }

    final File finalSsdDir = targetSsdDir;

    ContextWrapper wrapper = new ContextWrapper(base) {
      @Override
      public File getCacheDir() {
        if (finalSsdDir != null) {
          File customCache = new File(finalSsdDir, "cache");
          if (!customCache.exists()) customCache.mkdirs();
          return customCache;
        }
        return super.getCacheDir(); // 未找到固态硬盘时回退至系统默认
      }

      @Override
      public File getExternalCacheDir() {
        if (finalSsdDir != null) {
          File customExtCache = new File(finalSsdDir, "ext_cache");
          if (!customExtCache.exists()) customExtCache.mkdirs();
          return customExtCache;
        }
        return super.getExternalCacheDir();
      }
    };

    super.attachBaseContext(wrapper);
  }
  // --- 拦截逻辑结束 ---

  private final ReactNativeHost mReactNativeHost =
      new NavigationReactNativeHost(this) {
        @Override
        public boolean getUseDeveloperSupport() {
          return BuildConfig.DEBUG;
        }

        @Override
        protected List<ReactPackage> getPackages() {
          @SuppressWarnings("UnnecessaryLocalVariable")
          List<ReactPackage> packages = new PackageList(this).getPackages();
          // Packages that cannot be autolinked yet can be added manually here, for example:
          // packages.add(new MyReactNativePackage());
          packages.add(new CachePackage());
          packages.add(new LyricPackage());
          packages.add(new UtilsPackage());
          packages.add(new CryptoPackage());
          packages.add(new UserApiPackage());
          return packages;
        }

        @Override
        protected String getJSMainModuleName() {
          return "index";
        }

        @Override
        protected boolean isNewArchEnabled() {
          return BuildConfig.IS_NEW_ARCHITECTURE_ENABLED;
        }

        @Override
        protected Boolean isHermesEnabled() {
          return BuildConfig.IS_HERMES_ENABLED;
        }
      };

  @Override
  public ReactNativeHost getReactNativeHost() {
    return mReactNativeHost;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
      // If you opted-in for the New Architecture, we load the native entry point for this app.
      DefaultNewArchitectureEntryPoint.load();
    }
    ReactNativeFlipper.initializeFlipper(this, getReactNativeHost().getReactInstanceManager());
  }
}
