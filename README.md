# 鲁班机关匣（LubanBox）

面向 PICO OS 的手势机关解谜游戏。工作台上摆放中国古代机关器物，玩家通过捏合、推动、转动和拼装理解结构并打开机关。

## 本次提交

- 应用 ID：`com.vrgames.lubanbox`
- 版本：`0.7`，versionCode `7`
- 三章与终章，共 12 件器物：鲁班锁、华容道、鲁班盒、斗拱、文字锁、花旗锁、被中香炉、透光镜、指南车、浑天仪、水运仪象台、总机关匣。
- 提供器物说明卡、分级提示、营造图谱、自动存档、章节信物、音乐开关和台面高度调整。
- 使用拇指与食指捏合操作，不需要手柄；可切换真实房间透视背景与木作工坊全景背景。
- 启动时依据当前头部朝向将工作台放在面前；菜单提供重新居中。

## 构建

本目录是 Gradle 工程根目录，只有一个 Android application 模块 `app`。

```sh
./gradlew assembleRelease --no-daemon --max-workers=2
./gradlew testDebugUnitTest --no-daemon --max-workers=2
```

使用 Gradle 8.13、AGP 8.13.2、Android SDK 35、PICO Spatial BOM 6.1.9。Java/Kotlin 字节码目标为 11。配置标准 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，不要提交机器路径或 `local.properties`。

唯一发布产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`，由 PICO 构建并签名。提交副本去掉原开发者的可选私密签名配置；运行代码、资源、包名和版本保持原样。

## 素材

所有运行时模型、贴图和音频已预生成并放入 `app/src/main/`，构建不需要 Blender、Node 或素材服务凭证。原项目的授权清单见 `THIRD_PARTY_NOTICES.md`，其中 `tools/` 为原项目的离线素材制作工具说明，不是本提交构建依赖。

## 数据与输入

进度和设置保存在本机。应用没有声明网络权限，不要求登录。运行时使用系统提供的手部和头部追踪数据完成交互和工作台定位，不上传这些数据。
