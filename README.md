# LightData

Android 单屏取景器与胶片测光表。最低支持 Android 9，使用 Camera2 采集图像、Compose 排布控件、OpenGL ES 绘制预览。应用在本机处理图像，退出拼接或测光点模式后即可回到实时画面。

## 使用

- 点击取景器顶部的画幅和焦距，输入目标相机的数据。自动选镜会在手机开放的后置摄像头中选择能够覆盖目标视场的镜头。缺失视场显示为暗区，确认中心后按覆盖图转动手机，采满 115% 范围后即可小幅转动手机二次构图。
- 测光表的每一列是一组快门和光圈。列间保持同一 ISO 下的等效曝光，琥珀色标出当前组合。点击其他列可选用该组。A、S 档可在档位下方以 1/3 EV 步进拨动 −5 至 +5 EV 的补偿，正值增加胶片曝光。M 档显示手动设置的曝光偏差。横拖可微调 A 档光圈或 S 档快门。M 档分别拖动快门区和光圈区，改变曝光偏差。ISO 刻度独立调节，设置里可输入自定义数值。
- 点“连续”跟随场景，点“单次”采样并保持读数，再点“单次”重新测量。工具栏的测光点会冻结画面，在取景器中点按添加编号并标到直方图，重复点按同一位置可移除。直方图横轴表示场景的绝对 EV100，Zone V 位于当前目标曝光组合对应的 EV100。相机和胶片的设置变化会移动分区和曝光界限。横拖平移，双指水平或垂直拉开可分别放大两轴。红色斜线为过曝，绿色反向斜线为欠曝。
- 在设置里选择胶片及色彩映射，用工具栏“预览”切换胶片响应。在 HP5 PLUS 的长曝光列中，箭头指向实拍时间。PORTRA 400 和 E100 按各自厂家的适用范围提示。胶片外观、用户自定义的响应和厂家数据在设置中分别说明。[ILFORD 倒易律补偿指南](https://www.ilfordphoto.com/wp/wp-content/uploads/2024/05/Reciprocity-Failure-Compensation-v2.pdf)
- “组件比例”会显示可拖动的分隔线，工具栏点“完成”保存。竖屏与横屏独立记忆。

- 设置中的“直方图像素纵轴”可在线性与对数之间切换。对数模式用 log(1 + 像素数) 显示少量像素，右侧胶片密度 D 仍按原刻度显示。测光点与其直方图竖线按编号配色。
- 近方形大窗口（短边至少 540 dp，长短边比不超过 1.30）竖屏时左侧取景、右侧上下排列两块仪表；横屏时上方取景、下方左右排列仪表。两种内屏布局与普通屏幕分别保存分隔线位置。

## 测光与胶片曲线对齐

直方图横轴为场景 EV100。选定的快门、光圈和 ISO 决定 Zone V 的位置，胶片曲线 CSV 中的 0 EV 就放在该处。不同型号的中灰锚点依据见设置中的曲线说明。零补偿的 A/S 档尽量把取景框内的平均亮度放在 Zone V，精度受相机档位取整和可选范围影响。测光采用线性亮度求平均，先把两端各 2% 的亮度限制在相应分位数。高反差画面中，亮部会抬高平均读数，使主体落在 Zone V 左侧。点测光可查看主体分区；增加曝光补偿会使主体在胶片曲线上向右移动。

## 校准与资料

灰卡需均匀照明并填满画面；九级阶梯卡需水平排列，按提示输入级差；现场估测需静止且同时含有阴影和高光。每档曝光采两次，用实际快门和 ISO 计算响应，检查移动、噪声和饱和。可取得有效 RAW 时记录 RAW 动态范围，同时校准实时 YUV 预览的响应。其余成功结果标记为 YUV。估测失败会保留原有记录。测光和胶片映射均为现场参考，手机单帧已剪切的亮暗细节无法恢复。

内置 167 款胶片，其中 119 款采用厂家图采样 CSV，48 款明确标记为乳剂类别近似。手册通过设置中的外链查看。50 款提供倒易律依据。CSV 位于 app/src/main/resources/studio/lightdata，包含来源文献 ID、页码、通道、处理条件和中灰对齐方法。密度上下限为图示范围内的预览估算。[银盐图书馆](https://github.com/fuduji000-netizen/silvergrain-library)

## 构建和验证

安装 Android SDK Platform 37 和 Build Tools 36.0.0，配置 Android SDK 路径，并使用 Android Studio 附带的 JDK。Windows PowerShell 命令如下。

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

应用包名为 `studio.lightdata.guosuyzy`，可与原作者的 `studio.lightdata` 安装共存。调试包输出到 `app/build/outputs/apk/debug/app-debug.apk`。连接 Android 设备后运行 `.\gradlew.bat connectedDebugAndroidTest` 可验证逐镜头校准档案的写入、加载及隔离。

正式 Release 保留全部现有功能，关闭调试且不做代码或资源裁剪。签名密钥不得提交 Git：在项目根目录创建已被忽略的 `keystore.properties`，填写下面的本地配置，其中路径可使用正斜杠。

```properties
storeFile=C:/path/to/lightdata-release.jks
storePassword=替换为密钥库密码
keyPassword=替换为密钥密码
keyAlias=lightdata
```

配置后运行 `.\gradlew.bat assembleRelease`，得到 `app/build/outputs/apk/release/app-release.apk`。未配置时会生成未签名 Release APK，不能当作正式签名包分发；请用 Android SDK Build Tools 的 `apksigner verify --verbose` 验证。后续升级安装必须保留同一个密钥库，丢失后无法以该签名继续覆盖升级。

Phoenix II 的倒易律使用借用指数的经验估算，界面明确标为“估算”，不应视作厂家公式或补偿保证。其他型号的离散资料也不随快门档位插值；超出厂家给出的适用范围时保留“未公开”状态。

`.github/workflows/android-ci.yml` 会在推送、PR 和手工触发时运行 JVM 单元测试、Android lint、Debug APK 构建及仪器测试源码编译。真机或模拟器上的仪器测试仍需通过 `connectedDebugAndroidTest` 单独执行，源码编译不能代替相机、GL 与实际测量验收。

曝光密度数据已合并去重。彩色图的绿层或注明的三层均值用于亮度测光。额外分层数据覆盖 EKTAR 100、PORTRA 160、PORTRA 800，光谱感光度覆盖上述三款及 ULTRAMAX 400。光谱 CSV 保留 nm、log reciprocal exposure 和测量条件，密度 CSV 保留每层的相对中灰 EV 和密度。可通过 tools/digitize_kodak.py 从原手册重建这些数据。

分层预览将手机线性 RGB 以三个高斯可见光基函数近似重建，再按厂家感光谱积分，各层采用中性日光归一化，最后应用对应曝光密度与正像显示变换。手机 RGB 无法确定场景真实光谱，光源变化、同色异谱、红外和冲洗扫描差异会影响色彩。缺少可靠分层数据的型号继续使用其亮度曲线和所选配色。数据表中的 119 款采样并不代表 356 份资料都含可辨识曲线，历史版本与多型号手册在统计中分别处理。