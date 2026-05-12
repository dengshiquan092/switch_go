package com.example.switchgo

// ... 保持原有 import 不变 ...
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.widget.Button
import android.graphics.Color
import android.os.Looper
import kotlinx.coroutines.withContext
import android.widget.LinearLayout
import android.view.ViewGroup // 如果你使用了之前提到的 ViewGroup 遍历法，也需要这个

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

    // --- 新增：USB 权限相关变量 ---
    private val ACTION_USB_PERMISSION = "com.example.switchgo.USB_PERMISSION"
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            setMsg("USB 权限授予成功")
                            switchGo.openDevice()
                        }
                    } else {
                        setMsg("USB 权限被拒绝")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swtich_go)
        init()
    }


    override fun onResume() {
        super.onResume()
        // --- 修改：增加主动权限检查逻辑 ---
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        // 查找对应的设备 (VID: 1155)
        val device = usbManager.deviceList.values.find { it.vendorId == 1155 }

        if (device != null) {
            if (!usbManager.hasPermission(device)) {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
                usbManager.requestPermission(device, permissionIntent)
                setMsg("正在申请 USB 权限...")
            } else {
                switchGo.openDevice()
            }
        } else {
            switchGo.openDevice()
        }
    }

    private fun init() {
        val context = this
        scope.launch {
            delay(500)
            switchGo.getHidDevices()
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
            R.id.btn_space3,
            R.id.btn_all_door_open_switch_status,
            R.id.btn_all_door_close_switch_status
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

                val expectedDevices = mapOf(
                    "MCU-1 (Top)" to "1155,22336",
                    "MCU-2 (Bottom)" to "1155,22352",
                    "Temi Base/Rockchip" to "10071,4867",
                    "Android Accessory" to "7531,42151"
                )

                val cleanedUsbOutput = usbDevices.replace(" ", "")
                val missingDevices = expectedDevices.filter { entry ->
                    !cleanedUsbOutput.contains(entry.value)
                }

                // --- 修改开始 ---
                if (missingDevices.isEmpty()) {
                    // 全部找到：使用 ColorStateList 更新着色，覆盖掉之前的清除颜色
                    v?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.GREEN)
                    setMsg(">>> All devices matched successfully!")
                } else {
                    // 存在缺失：同理，使用着色
                    v?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.RED)

                    val missingNames = missingDevices.keys.joinToString(", ")
                    setMsg(">>> MISSING DEVICES: $missingNames")
                }
                // --- 修改结束 ---
            }
            R.id.bt_exit -> {
                openJob?.cancel()
                finish()
            }
            R.id.bt_clear_display -> {

                // 1. 定义原始颜色和 TintList
                val originColor = Color.parseColor("#D5D6D6")
                val colorList = ColorStateList.valueOf(originColor)

                // 2. 将所有需要处理的容器放入一个列表
                val containers = listOf(
                    findViewById<LinearLayout>(R.id.battery_led),
                    findViewById<LinearLayout>(R.id.light_control),
                    findViewById<LinearLayout>(R.id.devices_mcu)
                )

                // 3. 执行统一修改
                containers.forEach { container ->
                    // 遍历当前容器内的所有子 View
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        // 只有是按钮时才修改颜色
                        if (child is Button) {
                            child.backgroundTintList = colorList
                        }
                    }
                }

                setMsg("")
            }
            R.id.bt_hid_get_mcu1_version -> {
                scope.launch {
                    val mcu1 = this@SwitchGoActivity.switchGo.getMcuVersion(0x01)
                    val parts = mcu1.split(" ").filter { it.isNotBlank() }

                    // 切换到主线程更新 UI (setBackgroundColor 内部会自动切，但为了规范建议显式切换)
                    withContext(Dispatchers.Main) {
                        if (parts.size >= 10 &&
                            parts[6].equals("26", ignoreCase = true) &&
                            parts[7].equals("03", ignoreCase = true) &&
                            parts[8].equals("06", ignoreCase = true) &&
                            parts[9].equals("05", ignoreCase = true)) {

                            // 关键修改：使用 backgroundTintList 设为绿色
                            v?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.GREEN)
                            setMsg("MCU1 Version Match: 26 03 06 05")
                        } else {
                            // 关键修改：使用 backgroundTintList 设为红色
                            v?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.RED)
                            setMsg("MCU1 Version Mismatch or Error")
                        }
                    }
                    setMsg(mcu1)
                }
            }

            R.id.btn_all_door_open_switch_status -> {
                // 1. 首先弹出确认对话框
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("操作确认")
                    .setMessage("请确认全部的门已经处于开门状态")
                    .setPositiveButton("确定") { _, _ ->
                        // 2. 用户点击确定后，开始执行检测逻辑
                        scope.launch {

                            setMsg("")
                            val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                            setMsg("收到回复: $response")
                            val data = hexStringToByteArray(response)

                            var isAllValid = true
                            val errorMessages = mutableListOf<String>()

                            // 1. 验证电机状态 (index 5-8, 预期均为 1)
                            for (i in 5..8) {
                                val status = data[i].toInt()
                                if (status != 1) {
                                    isAllValid = false
                                    val msg = "电机${i - 4}状态异常: 预期 1, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 2. 验证开启状态 (index 10, 12, 14, 16, 预期均为 1)
                            val openIndices = intArrayOf(10, 12, 14, 16)
                            openIndices.forEachIndexed { index, dataIdx ->
                                val status = data[dataIdx].toInt()
                                if (status != 1) {
                                    isAllValid = false
                                    val msg = "门 ${index + 1} 开启状态异常: 预期 1, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 3. 验证关闭状态 (index 9, 11, 13, 15, 预期均为 0)
                            val closeIndices = intArrayOf(9, 11, 13, 15)
                            closeIndices.forEachIndexed { index, dataIdx ->
                                val status = data[dataIdx].toInt()
                                if (status != 0) {
                                    isAllValid = false
                                    val msg = "门 ${index + 1} 关闭状态异常: 预期 0, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 4. 验证堵塞报警状态 (index 22, 23, 24, 25, 预期均为 0)
                            val blockageIndices = intArrayOf(22, 23, 24, 25)
                            blockageIndices.forEachIndexed { index, dataIdx ->
                                val status = data[dataIdx].toInt()
                                if (status != 0) {
                                    isAllValid = false
                                    val msg = "门 ${index + 1} 堵塞报警异常: 预期 0, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 3. UI 更新结果
                            withContext(Dispatchers.Main) {
                                val btn = findViewById<Button>(R.id.btn_all_door_open_switch_status)
                                if (isAllValid) {
                                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                                    setMsg("检测通过：所有状态符合预期 (Green)")
                                    setMsg(parseResponse(response))
                                } else {
                                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
                                    val fullErrorLog = errorMessages.joinToString("\n")
                                    setMsg("门全部开启时,相关传感器检测失败 (Red):\n$fullErrorLog")
                                    setMsg(parseResponse(response))
                                }
                            }
                        }
                    }
                    .setNegativeButton("取消", null) // 点击取消则不做任何操作
                    .show()
            }


            R.id.btn_all_door_close_switch_status -> {
                // 1. 弹出确认对话框
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("操作确认")
                    .setMessage("请确认全部的门已经处于关闭状态")
                    .setPositiveButton("确定") { _, _ ->
                        // 2. 用户点击确定后，开始执行检测逻辑
                        scope.launch {
                            setMsg("")
                            val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                            setMsg("收到回复: $response") // 首先显示原始报文
                            val data = hexStringToByteArray(response)

                            var isAllValid = true
                            val errorMessages = mutableListOf<String>()

                            // 1. 验证电机状态 (index 5-8, 预期均为 0)
                            for (i in 5..8) {
                                val status = data[i].toInt()
                                if (status != 0) {
                                    isAllValid = false
                                    val msg = "电机${i - 4}状态异常: 预期 0, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 2. 验证开启状态 (index 10, 12, 14, 16, 预期均为 0)
                            val openIndices = intArrayOf(10, 12, 14, 16)
                            openIndices.forEachIndexed { index, dataIdx ->
                                val status = data[dataIdx].toInt()
                                if (status != 0) {
                                    isAllValid = false
                                    val msg = "门 ${index + 1} 开启状态异常: 预期 0, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 3. 验证关闭状态 (index 9, 11, 13, 15, 预期均为 1)
                            val closeIndices = intArrayOf(9, 11, 13, 15)
                            closeIndices.forEachIndexed { index, dataIdx ->
                                val status = data[dataIdx].toInt()
                                if (status != 1) {
                                    isAllValid = false
                                    val msg = "门 ${index + 1} 关闭状态异常: 预期 1, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 4. 验证堵塞报警状态 (index 22, 23, 24, 25, 预期均为 96)
                            val blockageIndices = intArrayOf(22, 23, 24, 25)
                            blockageIndices.forEachIndexed { index, dataIdx ->
                                val status = data[dataIdx].toInt()
                                if (status != 96) {
                                    isAllValid = false
                                    val msg = "门 ${index + 1} 堵塞报警异常: 预期 96, 实际 $status"
                                    errorMessages.add(msg)
                                    Log.e("SwitchGo", msg)
                                }
                            }

                            // 3. UI 更新结果
                            withContext(Dispatchers.Main) {
                                val btn = findViewById<Button>(R.id.btn_all_door_close_switch_status)
                                if (isAllValid) {
                                    // 全部符合预期
                                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                                    setMsg("检测通过：所有状态符合预期 (Green)")

                                    setMsg(parseResponse(response))
                                } else {
                                    // 存在异常
                                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
                                    // 将所有收集到的错误信息一次性显示在 UI 上
                                    val fullErrorLog = errorMessages.joinToString("\n")
                                    setMsg("门全部关闭时,相关传感器检测失败 (Red):\n$fullErrorLog")
                                    setMsg(parseResponse(response))
                                }
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }


            R.id.btn_space1 -> {
                scope.launch {
                    setMsg("")
                    val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                    setMsg("收到回复: $response") // 首先显示原始报文
                    val data = hexStringToByteArray(response)
                    // 获取状态值
                    val space1_status = data[17].toInt()

                    // 切换到主线程更新 UI
                    withContext(Dispatchers.Main) {
                        val btn = findViewById<Button>(R.id.btn_space1)
                        if (space1_status == 1) {
                            // 状态正常：变绿
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                            setMsg("槽型开关1状态正常")
                        } else {
                            // 状态异常：变红
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
                            val errorLog = "槽型开关1状态异常, 实际值: $space1_status"
                            setMsg(errorLog)
                            Log.e("SwitchGo", errorLog)
                        }
                    }
                }
            }

            R.id.btn_space2 -> {
                scope.launch {
                    setMsg("")
                    val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                    setMsg("收到回复: $response") // 首先显示原始报文
                    val data = hexStringToByteArray(response)
                    // 获取状态值
                    val space2_status = data[18].toInt()

                    // 切换到主线程更新 UI
                    withContext(Dispatchers.Main) {
                        val btn = findViewById<Button>(R.id.btn_space2)
                        if (space2_status == 1) {
                            // 状态正常：变绿
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                            setMsg("槽型开关2状态正常")
                        } else {
                            // 状态异常：变红
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
                            val errorLog = "槽型开关2状态异常, 实际值: $space2_status"
                            setMsg(errorLog)
                            Log.e("SwitchGo", errorLog)
                        }
                    }
                }
            }


            R.id.btn_space3 -> {
                scope.launch {
                    setMsg("")
                    val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                    setMsg("收到回复: $response") // 首先显示原始报文
                    val data = hexStringToByteArray(response)
                    // 获取状态值
                    val space3_status = data[19].toInt()

                    // 切换到主线程更新 UI
                    withContext(Dispatchers.Main) {
                        val btn = findViewById<Button>(R.id.btn_space3)
                        if (space3_status == 1) {
                            // 状态正常：变绿
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                            setMsg("槽型开关1状态正常")
                        } else {
                            // 状态异常：变红
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
                            val errorLog = "槽型开关3状态异常, 实际值: $space3_status"
                            setMsg(errorLog)
                            Log.e("SwitchGo", errorLog)
                        }
                    }
                }
            }

            R.id.bt_hid_get_mcu2_version -> {
                scope.launch {
                    val mcu2 = this@SwitchGoActivity.switchGo.getMcuVersion(0x02)
                    val parts = mcu2.split(" ").filter { it.isNotBlank() }

                    // 2. 判断长度是否足够并校验特定位置的值
                    // 索引 6-9 对应你说的第 7 组开始的 4 组数据
                    if (parts.size >= 10 &&
                        parts[6].equals("26", ignoreCase = true) &&
                        parts[7].equals("03", ignoreCase = true) &&
                        parts[8].equals("06", ignoreCase = true) &&
                        parts[9].equals("05", ignoreCase = true)) {

                        // 3. 匹配成功，将按钮背景设为绿色
                        v?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.GREEN)
                        setMsg("MCU2 Version Match: 26 03 06 05")
                    } else {
                        // 匹配失败，可以设为红色或保持原样
                        v?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.RED)
                        setMsg("MCU2 Version Mismatch or Error")
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
                switchGo.controllerTurnSignal(1, 0)

                // 这里的 v 通常是 onClick(View v) 传入的参数
                val currentView = v

                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("左转灯确认")
                    .setMessage("机器人前方左转灯和后方的左转灯是否都正常亮起？(需要关闭所有的门后在测试)")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.controllerTurnSignal(0,0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.controllerTurnSignal(0,0)
                    }
                    .setCancelable(false)
                    .show()


            }
            R.id.bt_switch_right_turn -> {
                //signal = if (signal == 1) 0 else 1
                switchGo.controllerTurnSignal(0, 1)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("右转灯确认")
                    .setMessage("机器人前方右转灯和后方的右转灯是否都正常亮起？(需要关闭所有的门后在测试)")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.controllerTurnSignal(0,0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.controllerTurnSignal(0,0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_rbg_mode1 -> {
                switchGo.toggleAmbientLight(1, 50, 50, 50)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("下方圆形灯带确认")
                    .setMessage("下方圆形灯带是否正在闪烁？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setCancelable(false)
                    .show()

            }
            R.id.bt_rbg_mode2 ->{
                switchGo.toggleAmbientLight(2, 255, 0, 0)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("下方圆形灯带确认")
                    .setMessage("下方圆形灯带是否正呼吸？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_rbg_mode3 -> {
                switchGo.toggleAmbientLight(3, 0, 255, 0)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("下方圆形灯带确认")
                    .setMessage("下方圆形灯带常亮？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_rbg_mode4 ->{
                switchGo.toggleAmbientLight(4, 0, 0, 255)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("下方圆形灯带确认")
                    .setMessage("下方圆形灯带是否颜色渐变？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.toggleAmbientLight(0, 0, 0, 0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_rbg_close -> switchGo.toggleAmbientLight(0, 0, 0, 0)
            R.id.bt_set_soc_0 -> {
                switchGo.setBatteryIndicator(0)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("后方电量指示灯确认")
                    .setMessage("后方电量指示灯是否是0%(灯全部不亮的状态)？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )

                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )

                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_set_soc_25 -> {
                switchGo.setBatteryIndicator(25)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("后方电量指示灯确认")
                    .setMessage("后方电量指示灯是否是25%(只有最下方1组灯是亮起的状态)？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_set_soc_50 -> {
                switchGo.setBatteryIndicator(50)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("后方电量指示灯确认")
                    .setMessage("后方电量指示灯是否是50%(只有最下方2组灯是亮起的状态)？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_set_soc_75 -> {
                switchGo.setBatteryIndicator(75)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("后方电量指示灯确认")
                    .setMessage("后方电量指示灯是否是75%(只有最下方3组灯是亮起的状态)？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.bt_set_soc_100 -> {
                switchGo.setBatteryIndicator(100)
                val currentView = v
                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("后方电量指示灯确认")
                    .setMessage("后方电量指示灯是否是100%(灯全部亮起)？")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.setBatteryIndicator(0)
                    }
                    .setCancelable(false)
                    .show()
            }
            R.id.get_all_switch -> {
                scope.launch {
                    val response = this@SwitchGoActivity.switchGo.getAllSwitchStates()
                    setMsg(response)

                    if (response.isNullOrBlank()) {
                        setMsg("未获得设备授权或设备未响应")
                        return@launch
                    }

                    // 检查 2: 字符串长度是否符合预期 (Hex 字符串长度应该是字节数的 2 倍)
                    // 既然你要访问 data[19]，那么 data 长度至少要 20，response 长度至少要 40
                    if (response.replace(" ", "").length < 40) {
                        setMsg("数据长度异常，请重新尝试")
                        return@launch
                    }

                    setMsg(parseResponse(response))
                }
            }
            R.id.get_switch_lamp -> {
                // 将门打开
//                switchGo.controllerAllDoors(1, 1, 1, 1)
                //interLight = if (interLight == 1) 0 else 1
                switchGo.toggleInteriorLight(1)

                // 这里的 v 通常是 onClick(View v) 传入的参数
                val currentView = v

                // 2. 弹出对话框确认状态
                androidx.appcompat.app.AlertDialog.Builder(currentView.context)
                    .setTitle("灯光确认")
                    .setMessage("内部灯是否都亮起？(需要打开所有的门后在测试)")
                    .setPositiveButton("YES") { _, _ ->
                        // 使用 backgroundTintList 修改为绿色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.GREEN
                        )
                        switchGo.toggleInteriorLight(0)
                    }
                    .setNegativeButton("NO") { _, _ ->
                        // 使用 backgroundTintList 修改为红色
                        currentView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.RED
                        )
                        switchGo.toggleInteriorLight(1)
                    }
                    .setCancelable(false)
                    .show()
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


    // 定义一个辅助函数，避免重复代码
    private fun updateButtonColor(button: Button, status: Int) {
        // 这里的日志能帮你确认数据是否真的解析对了
        setMsg("Debug - Space Status: $status")

        val color = when (status) {
            1 -> Color.GREEN       // 已安装
            2 -> Color.YELLOW      // 未安装
            else -> Color.RED      // 异常/未知
        }

        // 统一使用 TintList，这会覆盖之前的清除状态
        button.backgroundTintList = ColorStateList.valueOf(color)
    }

    /**
     * 将十六进制字符串转换为字节数组
     */
    private fun hexStringToByteArray(s: String): ByteArray {
        // 移除字符串中的空格（如果有）
        val cleanString = s.replace(" ", "")
        val len = cleanString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanString[i], 16) shl 4)
                    + Character.digit(cleanString[i + 1], 16)).toByte()
            i += 2
        }
        return data
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
        // --- 新增：注销广播防止内存泄漏 ---
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {}
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