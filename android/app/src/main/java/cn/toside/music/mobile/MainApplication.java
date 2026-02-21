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
            long totalBytes = statFs.getTotalBytes();
            
            if (totalBytes > THRESHOLD_BYTES) {
              targetSsdDir = dir;
              break; // 准确命中 512GB 固态硬盘，终止遍历
            }
          } catch (IllegalArgumentException e) {
            // 忽略未挂载成功或无访问权限的异常路径
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
          // 极致防御：仅当目录真实存在或成功创建时，才返回重定向路径
          if (customCache.exists() || customCache.mkdirs()) {
            return customCache;
          }
        }
        return super.getCacheDir(); // 硬盘异常时回退
      }

      @Override
      public File getFilesDir() {
        if (finalSsdDir != null) {
          File customFiles = new File(finalSsdDir, "files");
          if (customFiles.exists() || customFiles.mkdirs()) {
            return customFiles;
          }
        }
        return super.getFilesDir();
      }

      @Override
      public File getExternalCacheDir() {
        if (finalSsdDir != null) {
          File customExtCache = new File(finalSsdDir, "ext_cache");
          if (customExtCache.exists() || customExtCache.mkdirs()) {
            return customExtCache;
          }
        }
        return super.getExternalCacheDir();
      }

      @Override
      public File getNoBackupFilesDir() {
        if (finalSsdDir != null) {
          File customNoBackup = new File(finalSsdDir, "no_backup");
          if (customNoBackup.exists() || customNoBackup.mkdirs()) {
            return customNoBackup;
          }
        }
        return super.getNoBackupFilesDir();
      }

      @Override
      public File getDir(String name, int mode) {
        if (name != null) {
          String n = name.toLowerCase();
          // 【严格白名单】必须留在原生内部存储 (ext4/f2fs)，严禁重定向至 NTFS
          if (n.contains("webview") || 
              n.contains("textures") || 
              n.contains("dex") ||      // Dalvik/ART 缓存
              n.contains("lib") ||      // .so 动态链接库
              n.contains("ssl") ||      // 底层网络安全证书
              n.contains("chrome") ||   // 内核渲染目录
              n.contains("metrics") ||  // 性能监控底层日志
              n.contains("geolocation")) { // 定位缓存
            return super.getDir(name, mode);
          }

          // 拦截其余未命中白名单的目录，重定向至 SSD
          if (finalSsdDir != null) {
            File customDir = new File(finalSsdDir, "app_" + name);
            if (customDir.exists() || customDir.mkdirs()) {
              return customDir;
            }
          }
        }
        // 如果 name 为 null，或者固态硬盘写入失败，均在此托底
        return super.getDir(name, mode);
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
