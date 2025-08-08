# detekt

## Metrics

* 92 number of properties

* 70 number of functions

* 6 number of classes

* 4 number of packages

* 20 number of kt files

## Complexity Report

* 2,028 lines of code (loc)

* 1,443 source lines of code (sloc)

* 1,081 logical lines of code (lloc)

* 400 comment lines of code (cloc)

* 130 cyclomatic complexity (mcc)

* 53 cognitive complexity

* 16 number of total code smells

* 27% comment source ratio

* 120 mcc per 1,000 lloc

* 14 code smells per 1,000 lloc

## Findings (16)

### complexity, LargeClass (1)

One class should have one responsibility. Large classes tend to handle many things at once. Split up large classes into smaller classes that are easier to understand.

[Documentation](https://detekt.dev/docs/rules/complexity#largeclass)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:24:7
```
Class FatigueViewModel is too large. Consider splitting it into smaller pieces.
```
```kotlin
21  * 負責所有疲勞檢測相關的 UI 狀態管理
22  * 包括疲勞級別、校正狀態、對話框等
23  */
24 class FatigueViewModel(
!!       ^ error
25     application: Application,
26 ) : AndroidViewModel(application), FatigueUiCallback {
27     companion object {

```

### complexity, LongParameterList (2)

The more parameters a function has the more complex it is. Long parameter lists are often used to control complex algorithms and violate the Single Responsibility Principle. Prefer functions with short parameter lists.

[Documentation](https://detekt.dev/docs/rules/complexity#longparameterlist)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/common/DrowsyGuardDialog.kt:32:22
```
The function DrowsyGuardDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmText: String, dismissText: String, titleColor: Color, messageColor: Color, showDismissButton: Boolean) has too many parameters. The current threshold is set to 8.
```
```kotlin
29  * @param showDismissButton 是否顯示取消按鈕，默認為 true
30  */
31 @Composable
32 fun DrowsyGuardDialog(
!!                      ^ error
33     title: String,
34     message: String,
35     onConfirm: () -> Unit,

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/common/DrowsyGuardDialog.kt:96:31
```
The function DrowsyGuardTwoButtonDialog(title: String, message: String, primaryButtonText: String, secondaryButtonText: String, onPrimaryClick: () -> Unit, onSecondaryClick: () -> Unit, onDismiss: () -> Unit, titleColor: Color, messageColor: Color) has too many parameters. The current threshold is set to 8.
```
```kotlin
93   * @param messageColor 消息顏色
94   */
95  @Composable
96  fun DrowsyGuardTwoButtonDialog(
!!                                ^ error
97      title: String,
98      message: String,
99      primaryButtonText: String,

```

### complexity, TooManyFunctions (1)

Too many functions inside a/an file/class/object/interface always indicate a violation of the single responsibility principle. Maybe the file/class/object/interface wants to manage too many things at once. Extract functionality which clearly belongs together.

[Documentation](https://detekt.dev/docs/rules/complexity#toomanyfunctions)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:24:7
```
Class 'FatigueViewModel' with '26' functions detected. Defined threshold inside classes is set to '15'
```
```kotlin
21  * 負責所有疲勞檢測相關的 UI 狀態管理
22  * 包括疲勞級別、校正狀態、對話框等
23  */
24 class FatigueViewModel(
!!       ^ error
25     application: Application,
26 ) : AndroidViewModel(application), FatigueUiCallback {
27     companion object {

```

### exceptions, TooGenericExceptionCaught (3)

The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.

[Documentation](https://detekt.dev/docs/rules/exceptions#toogenericexceptioncaught)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:196:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
193                 )
194             Log.d(TAG, "調試報告已生成")
195             report
196         } catch (e: Exception) {
!!!                  ^ error
197             Log.e(TAG, "生成調試報告失敗", e)
198             "生成調試報告失敗: ${e.message}"
199         }

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:211:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
208             val filePath = debugger.saveDebugReport(report)
209             Log.d(TAG, "調試報告已保存: $filePath")
210             filePath
211         } catch (e: Exception) {
!!!                  ^ error
212             Log.e(TAG, "保存調試報告失敗", e)
213             "保存失敗: ${e.message}"
214         }

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:260:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
257                 Log.d(TAG, "自動報告已生成")
258             }
259             report
260         } catch (e: Exception) {
!!!                  ^ error
261             Log.e(TAG, "檢查自動報告失敗", e)
262             null
263         }

```

### potential-bugs, ImplicitDefaultLocale (2)

Implicit default locale used for string processing. Consider using explicit locale.

[Documentation](https://detekt.dev/docs/rules/potential-bugs#implicitdefaultlocale)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:295:42
```
String.format("%.3f", avgEar) uses implicitly default locale for string formatting.
```
```kotlin
292         _isCalibrating.value = false
293         _calibrationProgress.value = 100
294         _calibrationEarValue.value = avgEar
295         _statusText.value = "校正完成！EAR: ${String.format("%.3f", avgEar)}, 閾值: ${String.format("%.3f", newThreshold)}"
!!!                                          ^ error
296     }
297 
298     override fun onNoticeFatigue() {

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:295:80
```
String.format("%.3f", newThreshold) uses implicitly default locale for string formatting.
```
```kotlin
292         _isCalibrating.value = false
293         _calibrationProgress.value = 100
294         _calibrationEarValue.value = avgEar
295         _statusText.value = "校正完成！EAR: ${String.format("%.3f", avgEar)}, 閾值: ${String.format("%.3f", newThreshold)}"
!!!                                                                                ^ error
296     }
297 
298     override fun onNoticeFatigue() {

```

### style, MaxLineLength (1)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueUiStateManager.kt:50:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
47     ): FatigueLevel {
48         Log.d(
49             TAG,
50             "處理疲勞結果: rawLevel=$rawFatigueLevel, eventCount=$fatigueEventCount, resetProtection=$isInResetProtection, cooldown=$isInCooldownPeriod",
!! ^ error
51         )
52 
53         // 檢查重置保護期

```

### style, ReturnCount (1)

Restrict the number of return statements in methods.

[Documentation](https://detekt.dev/docs/rules/style#returncount)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueUiStateManager.kt:43:9
```
Function processFatigueResult has 5 return statements which exceeds the limit of 2.
```
```kotlin
40     /**
41      * 處理疲勞檢測結果
42      */
43     fun processFatigueResult(
!!         ^ error
44         rawFatigueLevel: FatigueLevel,
45         fatigueEventCount: Int,
46         currentTime: Long = System.currentTimeMillis(),

```

### style, TrailingWhitespace (4)

Whitespaces at the end of a line are unnecessary and can be removed.

[Documentation](https://detekt.dev/docs/rules/style#trailingwhitespace)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:334:1
```
Line 334 ends with a whitespace.
```
```kotlin
331         // 只有在沒有警告視窗時，才切換 UI 狀態
332         // 這樣可以避免警告視窗彈出後，臉部暫時消失導致視窗被關閉
333         val hasActiveDialog = _showFatigueDialog.value || fatigueUiStateManager.hasActiveWarningDialog()
334         
!!! ^ error
335         if (!hasActiveDialog) {
336             Log.d(TAG, "臉部消失且無警告視窗，切換 UI 狀態")
337             updateUIState(FatigueLevel.NORMAL, false, "請面對鏡頭", false)

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/main/java/com/patrick/ui/fatigue/FatigueViewModel.kt:397:2
```
Line 397 ends with a whitespace.
```
```kotlin
394     fun getResetStatusInfo(): String {
395         return fatigueUiStateManager.getResetStatusInfo()
396     }
397 } 
!!!  ^ error
398 

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/test/java/com/patrick/ui/fatigue/FatigueUiStateManagerTest.kt:80:2
```
Line 80 ends with a whitespace.
```
```kotlin
77         assertFalse(manager.isInResetProtection())
78         assertFalse(manager.isInCooldownPeriod())
79     }
80 } 
!!  ^ error
81 

```

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/test/java/com/patrick/ui/fatigue/FatigueViewModelResetTest.kt:66:2
```
Line 66 ends with a whitespace.
```
```kotlin
63         assertTrue(statusInfo.contains("ResetProtection"))
64         assertTrue(statusInfo.contains("Cooldown"))
65     }
66 } 
!!  ^ error
67 

```

### style, UnusedPrivateProperty (1)

Property is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedprivateproperty)

* /Users/xuzhehao/AndroidStudioProjects/DrowsyGuard/ui-components/src/test/java/com/patrick/ui/fatigue/FatigueViewModelResetTest.kt:30:17
```
Private property `initialCount` is unused.
```
```kotlin
27     fun `test handleUserAcknowledged resets fatigue events`() =
28         runTest {
29             // Given: 模擬疲勞事件計數
30             val initialCount = 5
!!                 ^ error
31             // 這裡需要模擬 FatigueDetectionManager 的行為
32 
33             // When: 用戶確認已清醒

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-08-07 23:14:01 UTC
