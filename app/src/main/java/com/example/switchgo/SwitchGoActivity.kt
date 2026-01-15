package com.example.switchgo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rhizo.switchgo.McuUpdateCallback
import com.rhizo.switchgo.SwitchGo
import com.robotemi.sdk.BatteryData
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnBatteryStatusChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SwitchGoActivity : AppCompatActivity(), View.OnClickListener, McuUpdateCallback {
    private lateinit var rtti: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var switchGo: SwitchGo
    private val scope = lifecycleScope
    private var gate1 = 2
    private var gate2 = 2
    private var gate3 = 2
    private var gate4 = 2
    private var allGate = 2
    private var signal: Int = 0
    private var interLight: Int = 0
    private var openJob: Job? = null
    private var invisibleButtonClickCount = 0
    private val INVISIBLE_BUTTON_MAX_CLICKS = 5
    private val mutableStringList: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swtich_go)
        init()
    }


    override fun onResume() {
        super.onResume()
        switchGo.openDevice()
    }

    private fun init() {
        val context = this
        scope.launch {
            copyAssetsToInternalStorage(context,arrayOf("SwitchGoTopApp.bin","SwitchGoBottomApp.bin"))
        }
        switchGo = SwitchGo.getInstance(this)
        switchGo.registerCallback(this)
        scrollView = findViewById(R.id.scrollView)
        rtti = findViewById(R.id.txt_sendPostRequestByForm)
        val buttonIds = arrayOf(
            R.id.bt_hid_get_mcu1_version,
            R.id.bt_hid_get_mcu2_version,
            R.id.bt_switch_gate1,
            R.id.bt_switch_gate2,
            R.id.bt_switch_gate3,
            R.id.bt_switch_gate4,
            R.id.bt_all_gates,
            R.id.bt_switch_left_turn,
            R.id.bt_switch_right_turn,
            R.id.bt_rbg_mode1,
            R.id.bt_rbg_mode2,
            R.id.bt_rbg_mode3,
            R.id.bt_rbg_mode4,
            R.id.bt_rbg_close,
            R.id.bt_set_soc_0,
            R.id.bt_set_soc_25,
            R.id.bt_set_soc_50,
            R.id.bt_set_soc_75,
            R.id.bt_set_soc_100,
            R.id.get_all_switch,
            R.id.get_switch_lamp,
            R.id.bt_version_update,
            R.id.bt_version_update2,
            R.id.bt_clear_display,
            R.id.bt_exit,
            R.id.bt_mcu_reboot,
            R.id.bt_clear_error,
            R.id.bt_open_door_test,
            R.id.bt_patrol,
            R.id.bt_stop_patrol,
            R.id.bt_get_hid_usb,
            R.id.invisible_button,
            R.id.btn_space1,
            R.id.btn_space2,
            R.id.btn_space3
        )
        buttonIds.forEach { id ->
            findViewById<View>(id)?.setOnClickListener(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.invisible_button -> {
                invisibleButtonClickCount++
                if (invisibleButtonClickCount >= INVISIBLE_BUTTON_MAX_CLICKS) {
                    invisibleButtonClickCount = 0
                    val updateMucLayout = findViewById<View>(R.id.update_muc_ll)
                    updateMucLayout.visibility = View.VISIBLE
                }
            }
            R.id.bt_get_hid_usb -> {
                val usbDevices = switchGo.getHidDevices()
                setMsg(usbDevices)

                // 1. 定义预期的设备清单（Key为显示名称，Value为匹配特征字符串）
                val expectedDevices = mapOf(
                    "MCU-1 (Top)" to "1155,22336",
                    "MCU-2 (Bottom)" to "1155,22352",
                    "Temi Base/Rockchip" to "10071,4867",
                    "Android Accessory" to "7531,42151"
                )

                // 2. 预处理返回的字符串，去掉空格以防匹配干扰
                val cleanedUsbOutput = usbDevices.replace(" ", "")

                // 3. 筛选出“未找到”的设备
                val missingDevices = expectedDevices.filter { entry ->
                    !cleanedUsbOutput.contains(entry.value)
                }

                // 4. 根据结果更新 UI
                if (missingDevices.isEmpty()) {
                    // 全部找到
                    v?.setBackgroundColor(android.graphics.Color.GREEN)
                    setMsg(">>> All devices matched successfully!")
                } else {
                    // 存在缺失
                    v?.setBackgroundColor(android.graphics.Color.RED)

                    // 罗列出具体缺少的设备名称
                    val missingNames = missingDevices.keys.joinToString(", ")
                    setMsg(">>> MISSING DEVICES: $missingNames")

                    // 也可以逐行打印更详细的警告
                    missingDevices.forEach { (name, id) ->
                        Log.e("USB_CHECK", "Missing hardware: $name (Expected ID: $id)")
                    }
                }
            }
            R.id.bt_exit -> {
                openJob?.cancel()
                finish()
            }
            R.id.bt_clear_display -> setMsg("")
            R.id.bt_hid_get_mcu1_version -> {
                scope.launch {
                    val mcu1 = this@SwitchGoActivity.switchGo.getMcuVersion(0x01)

                    // 1. 将返回的字符串按空格分割成列表
                    val parts = mcu1.split(" ").filter { it.isNotBlank() }

                    // 2. 判断长度是否足够并校验特定位置的值
                    // 索引 6-9 对应你说的第 7 组开始的 4 组数据
                    if (parts.size >= 10 &&
                        parts[6].equals("AB", ignoreCase = true) &&
                        parts[7].equals("25", ignoreCase = true) &&
                        parts[8].equals("12", ignoreCase = true) &&
                        parts[9].equals("02", ignoreCase = true)) {

                        // 3. 匹配成功，将按钮背景设为绿色
                        v?.setBackgroundColor(android.graphics.Color.GREEN)
                        setMsg("MCU1 Version Match: AB 25 12 02")
                    } else {
                        // 匹配失败，可以设为红色或保持原样
                        v?.setBackgroundColor(android.graphics.Color.RED)
                        setMsg("MCU1 Version Mismatch or Error")
                    }

                    setMsg(mcu1)
                }
            }

            R.id.bt_hid_get_mcu2_version -> {
                scope.launch {
                    val mcu2 = this@SwitchGoActivity.switchGo.getMcuVersion(0x02)
                    val parts = mcu2.split(" ").filter { it.isNotBlank() }

                    // 2. 判断长度是否足够并校验特定位置的值
                    // 索引 6-9 对应你说的第 7 组开始的 4 组数据
                    if (parts.size >= 10 &&
                        parts[6].equals("AC", ignoreCase = true) &&
                        parts[7].equals("25", ignoreCase = true) &&
                        parts[8].equals("12", ignoreCase = true) &&
                        parts[9].equals("02", ignoreCase = true)) {

                        // 3. 匹配成功，将按钮背景设为绿色
                        v?.setBackgroundColor(android.graphics.Color.GREEN)
                        setMsg("MCU1 Version Match: AB 25 12 02")
                    } else {
                        // 匹配失败，可以设为红色或保持原样
                        v?.setBackgroundColor(android.graphics.Color.RED)
                        setMsg("MCU1 Version Mismatch or Error")
                    }
                    setMsg(mcu2)
                }
            }
            R.id.bt_switch_gate1 -> {
                gate1 = if (gate1 == 2) 1 else 2
                switchGo.controllerAllDoors(gate1, 0, 0, 0)
            }
            R.id.bt_switch_gate2 -> {
                gate2 = if (gate2 == 2) 1 else 2
                switchGo.controllerAllDoors(0, gate2, 0, 0)
            }
            R.id.bt_switch_gate3 -> {
                gate3 = if (gate3 == 2) 1 else 2
                switchGo.controllerAllDoors(0, 0, gate3, 0)
            }
            R.id.bt_switch_gate4 -> {
                gate4 = if (gate4 == 2) 1 else 2
                switchGo.controllerAllDoors(0, 0, 0, gate4)
            }
            R.id.bt_all_gates -> {
                allGate = if (allGate == 2) 1 else 2
                switchGo.controllerAllDoors(allGate, allGate, allGate, allGate)
            }
            R.id.bt_switch_left_turn -> {
                signal = if (signal == 0) 1 else 0
                switchGo.controllerTurnSignal(signal, 0)
            }
            R.id.bt_switch_right_turn -> {
                signal = if (signal == 1) 0 else 1
                switchGo.controllerTurnSignal(0, signal)
            }
            R.id.bt_rbg_mode1 -> switchGo.toggleAmbientLight(1, 50, 50, 50)
            R.id.bt_rbg_mode2 -> switchGo.toggleAmbientLight(2, 255, 0, 0)
            R.id.bt_rbg_mode3 -> switchGo.toggleAmbientLight(3, 0, 255, 0)
            R.id.bt_rbg_mode4 -> switchGo.toggleAmbientLight(4, 0, 0, 255)
            R.id.bt_rbg_close -> switchGo.toggleAmbientLight(0, 0, 0, 0)
            R.id.bt_set_soc_0 -> switchGo.setBatteryIndicator(0)
            R.id.bt_set_soc_25 -> switchGo.setBatteryIndicator(25)
            R.id.bt_set_soc_50 -> switchGo.setBatteryIndicator(50)
            R.id.bt_set_soc_75 -> switchGo.setBatteryIndicator(75)
            R.id.bt_set_soc_100 -> switchGo.setBatteryIndicator(100)
            R.id.get_all_switch -> {
                scope.launch {
                    val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                    setMsg(response)
                    setMsg(parseResponse(response))
                }
            }
            R.id.get_switch_lamp -> {
                interLight = if (interLight == 1) 0 else 1
                switchGo.toggleInteriorLight(interLight)
            }
            R.id.bt_mcu_reboot -> {
                scope.launch {
                    val resp = this@SwitchGoActivity.switchGo.rebootMcu(1)
                }
            }
            R.id.bt_clear_error -> {
                scope.launch {
                    this@SwitchGoActivity.switchGo.clearDoorBlockAlert(0x01)
                    delay(10)
                    this@SwitchGoActivity.switchGo.clearDoorBlockAlert(0x02)
                    delay(10)
                    this@SwitchGoActivity.switchGo.clearDoorBlockAlert(0x03)
                    delay(10)
                    switchGo.clearDoorBlockAlert(0x04)
                }
            }
            // Note: Using the update function requires the App to have USB permissions by default, otherwise the MCU upgrade will fail.
            R.id.bt_version_update -> switchGo.updateMcuVersion(1, "${this.filesDir}/SwitchGoTopApp.bin")
            R.id.bt_version_update2 -> switchGo.updateMcuVersion(2, "${this.filesDir}/SwitchGoBottomApp.bin")
        }
    }






    override fun onDestroy() {
        super.onDestroy()
        switchGo.removeCallback(this)
        switchGo.closeDevice()
    }

    @Synchronized
    private fun setMsg(msg: String) {
        if (msg.isEmpty()) {
            rtti.text = msg
            mutableStringList.clear()
            return
        }

        mutableStringList.add(msg)
        val text = mutableStringList.joinToString("\r\n")

        Handler(mainLooper).post {
            rtti.text = text
            scrollView.fullScroll(View.FOCUS_DOWN)
        }

        if (mutableStringList.size > 20) mutableStringList.removeAt(0)
    }

    override fun onUpdateStart() {
        setMsg("Starting update")
    }


    /**
     * 将 assets 中的二进制文件复制到应用的内部存储目录
     * @param context Context 对象
     * @param binFiles 要复制的文件名数组（如 ["SwitchGoTopApp.bin", "SwitchGoBottomApp.bin"]）
     * @return Boolean 是否全部文件复制成功
     */
    fun copyAssetsToInternalStorage(context: Context, binFiles: Array<String>): Boolean {
        var allSuccess = true
        binFiles.forEach { fileName ->
            try {
                context.assets.open(fileName).use { inputStream ->
                    val outputFile = File(context.filesDir, fileName)
                    FileOutputStream(outputFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                allSuccess = false
            }
        }
        return allSuccess
    }

    override fun onPause() {
        super.onPause()

    }

    override fun onProgress(len: Long) {
        setMsg("Remaining send length: $len")
    }

    override fun onFail(str: String) {
        setMsg("Update failed: $str")
    }

    override fun onSuccess() {
        setMsg("Update successful")
    }



    fun parseResponse(response: String): String {
        val parts = response.split(" ").filter { it.isNotBlank() }
        if (parts.size < 30) { // 增加了前面命令符的长度
            return "Data format error, expected at least 30 parameters, actually received: ${parts.size}"
        }

        val result = StringBuilder()

        result.append(parseDoorMotorStates(parts))
        result.append("\n")

        result.append(parseOpenLimitStates(parts))
        result.append("\n")

        result.append(parseCloseLimitStates(parts))
        result.append("\n")

        result.append(parseSpaceStates(parts))
        result.append("\n")

        result.append(parseOtherStates(parts))

        return result.toString()
    }

    private fun parseDoorMotorStates(parts: List<String>): String {
        val result = StringBuilder()
        result.append("Door Motor Status:\n")

        // 跳过前面的命令符 0x30 0x00 0x00 0x16 0x84，从第5个开始是G1
        val g1 = parseHex(parts.getOrNull(5))   // G1
        val g2 = parseHex(parts.getOrNull(6))   // G2
        val g3 = parseHex(parts.getOrNull(7))   // G3
        val g4 = parseHex(parts.getOrNull(8))   // G4

        result.append("G1: ${getDoorMotorStateDescription(g1)}\n")
        result.append("G2: ${getDoorMotorStateDescription(g2)}\n")
        result.append("G3: ${getDoorMotorStateDescription(g3)}\n")
        result.append("G4: ${getDoorMotorStateDescription(g4)}")

        return result.toString()
    }

    private fun parseOpenLimitStates(parts: List<String>): String {
        val result = StringBuilder()
        result.append("Open Limit Status:\n")
        val g1so = parseHex(parts.getOrNull(10))   // G1SO
        val g2so = parseHex(parts.getOrNull(12))  // G2SO
        val g3so = parseHex(parts.getOrNull(14))  // G3SO
        val g4so = parseHex(parts.getOrNull(16))  // G4SO
        result.append("G1SO: ${getLimitStateDescription(g1so)}\n")
        result.append("G2SO: ${getLimitStateDescription(g2so)}\n")
        result.append("G3SO: ${getLimitStateDescription(g3so)}\n")
        result.append("G4SO: ${getLimitStateDescription(g4so)}")

        return result.toString()
    }

    private fun parseCloseLimitStates(parts: List<String>): String {
        val result = StringBuilder()
        result.append("Close Limit Status:\n")
        val g1sc = parseHex(parts.getOrNull(9))  // G1SC
        val g2sc = parseHex(parts.getOrNull(11))  // G2SC
        val g3sc = parseHex(parts.getOrNull(13))  // G3SC
        val g4sc = parseHex(parts.getOrNull(15))  // G4SC

        result.append("G1SC: ${getLimitStateDescription(g1sc,true)}\n")
        result.append("G2SC: ${getLimitStateDescription(g2sc,true)}\n")
        result.append("G3SC: ${getLimitStateDescription(g3sc,true)}\n")
        result.append("G4SC: ${getLimitStateDescription(g4sc,true)}")

        return result.toString()
    }

    private fun parseSpaceStates(parts: List<String>): String {
        val result = StringBuilder()
        result.append("Space Panel Status:\n")
        val space1 = parseHex(parts.getOrNull(17))  // SPACE1
        val space2 = parseHex(parts.getOrNull(18))  // SPACE2
        val space3 = parseHex(parts.getOrNull(19))  // SPACE3

        result.append("SPACE1: ${getSpaceStateDescription(space1)}\n")
        result.append("SPACE2: ${getSpaceStateDescription(space2)}\n")
        result.append("SPACE3: ${getSpaceStateDescription(space3)}")

        return result.toString()
    }

    private fun parseOtherStates(parts: List<String>): String {
        val result = StringBuilder()
        result.append("Other Status:\n")
        val soc = parseHex(parts.getOrNull(20))        // SOC
        val auxiliary = parseHex(parts.getOrNull(21))  // AUXILIARY
        val g1r = parseHex(parts.getOrNull(22))        // G1R
        val g2r = parseHex(parts.getOrNull(23))        // G2R
        val g3r = parseHex(parts.getOrNull(24))        // G3R
        val g4r = parseHex(parts.getOrNull(25))        // G4R

        result.append("SOC: ${getSOCDescription(soc)}\n")
        result.append("AUXILIARY: ${getAuxiliaryDescription(auxiliary)}\n")
        result.append("G1R: ${getDoorResistanceDescription(g1r)}\n")
        result.append("G2R: ${getDoorResistanceDescription(g2r)}\n")
        result.append("G3R: ${getDoorResistanceDescription(g3r)}\n")
        result.append("G4R: ${getDoorResistanceDescription(g4r)}")

        return result.toString()
    }

    private fun parseHex(hexString: String?): Int {
        return try {
            hexString?.toIntOrNull(16) ?: 0xFF
        } catch (e: Exception) {
            0xFF
        }
    }

    private fun getDoorMotorStateDescription(state: Int): String {
        return when (state) {
            0x00 -> "00 -> Motor stopped"
            0x01 -> "01 -> Motor powered and running"
            0x03 -> "03 -> Motor powered but not rotating"
            0x04 -> "04 -> Motor not powered but detected rotation"
            0xFF -> "FF -> Unknown status"
            else -> "Undefined status(0x${state.toString(16).uppercase()})"
        }
    }

    private fun getLimitStateDescription(state: Int, isClose: Boolean = false): String {
        return when (state) {
            0x00 -> "0 -> Limit not triggered"
            0x01 ->   if(isClose) "1 ->  Limit triggered, door closed " else "1 ->  Limit triggered, door open"
            0xFF -> "FF -> Unknown limit status"
            else -> "Undefined limit status(0x${state.toString(16).uppercase()})"
        }
    }

    private fun getSpaceStateDescription(state: Int): String {
        return when (state) {
            0x01 -> "1 -> Space panel installed"
            0x02 -> "2 -> Space panel not installed"
            0xFF -> "FF -> Unknown space panel status"
            else -> "Undefined space panel status(0x${state.toString(16).uppercase()})"
        }
    }

    private fun getSOCDescription(soc: Int): String {
        return if (soc == 0xFF) {
            "Battery indicator module disconnected"
        } else {
            "Battery indicator: $soc"
        }
    }

    private fun getAuxiliaryDescription(auxiliary: Int): String {
        return if (auxiliary == 0xFF) {
            "Slave device disconnected"
        } else {
            "Slave device connected normally"
        }
    }

    private fun getDoorResistanceDescription(state: Int): String {
        return if (state == 0x81) {
            "81 -> Door closing resistance alarm"
        } else {
            "Normal(0x${state.toString(16).uppercase()})"
        }
    }
}


