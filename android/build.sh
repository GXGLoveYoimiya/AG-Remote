#!/bin/bash
# 编译 AG Remote release APK。产物：app/build/outputs/apk/release/app-release.apk（另复制为 DIST/AGRemote.apk）
# 可用环境变量覆盖：
#   GRADLE     gradle 可执行文件，默认仓库上两级 _toolchain/gradle-9.9.2/bin/gradle，没有就用 PATH 里的 gradle
#   JAVA_HOME  JDK，没设置时如果有 C:/Program Files/Java/latest/jdk-27 就用它
#   SIGN_PROPS 签名配置，见 app/build.gradle
#   DIST       APK 另外复制到的目录，默认仓库上两级（_toolchain 所在目录）；设成空字符串就不复制
cd "$(dirname "$0")"
WS="$(cd ../../.. && pwd)"
if [ -z "$GRADLE" ]; then
  if [ -x "$WS/_toolchain/gradle-9.9.2/bin/gradle" ]; then GRADLE="$WS/_toolchain/gradle-9.9.2/bin/gradle"; else GRADLE=gradle; fi
fi
if [ -z "$JAVA_HOME" ] && [ -d "/c/Program Files/Java/latest/jdk-27" ]; then export JAVA_HOME="/c/Program Files/Java/latest/jdk-27"; fi
[ -z "${DIST+x}" ] && [ -d "$WS/_toolchain" ] && DIST="$WS"
"$GRADLE" assembleRelease --no-daemon --console=plain -q "$@" || exit 1
APK=app/build/outputs/apk/release/app-release.apk
echo "OK: $(pwd)/$APK"
if [ -n "$DIST" ] && [ -d "$DIST" ]; then cp "$APK" "$DIST/AGRemote.apk" && echo "已复制到 $DIST/AGRemote.apk"; fi
