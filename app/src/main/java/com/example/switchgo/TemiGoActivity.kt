package com.example.switchgo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
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

class TemiGoActivity : AppCompatActivity(), View.OnClickListener, McuUpdateCallback,
    OnGoToLocationStatusChangedListener, OnBatteryStatusChangedListener,
    OnRequestPermissionResultListener {
    private lateinit var rtti: TextView
    private lateinit var switchGo: SwitchGo
    private val scope = lifecycleScope
    private val robot = Robot.getInstance()
    private val CODE_REUQEST_TEMI_PERMISSION = 100
    private var mapLocations: ArrayList<String> = ArrayList()

    // Door control status
    private var gate1 = 2
    private var gate2 = 2
    private var gate3 = 2
    private var gate4 = 2
    private var allGate = 2

    // Signal light status
    private var signal: Int = 0
    private var interLight: Int = 0
    private var openJob: Job? = null
    private val HOME_BASE = "home base"
    private var locationIndex: Int = 0
    private var gate1OpenCount = 0
    private var gate2OpenCount = 0
    private var gate3OpenCount = 0
    private var gate4OpenCount = 0
    private var gateAllOpenCount = 0
    private var invisibleButtonClickCount = 0
    private val INVISIBLE_BUTTON_MAX_CLICKS = 5
    private val mutableStringList: MutableList<String> = mutableListOf()


    private fun requestPermissions() {
        if (this.applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ), 0x12345
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_temi_go)
        initView()
        init()
    }

    private fun getLocations() {
        scope.launch(Dispatchers.IO) {
            delay(1000)
            mapLocations = robot.locations as ArrayList<String>
            mapLocations.removeIf { it.equals(HOME_BASE, ignoreCase = true) }
            switchGo.setBatteryIndicator(100)
        }
    }

    private fun checkTemiAllPermission(): Boolean {
        val permissionCount = Permission.entries.toTypedArray().count {
            Robot.getInstance()
                .checkSelfPermission(it) != Permission.GRANTED && it != Permission.UNKNOWN
        }
        if (permissionCount == 0) {
            return true
        }
        return false
    }

    private fun initView() {
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
            R.id.bt_version_update3,
            R.id.bt_version_update4,
            R.id.bt_clear_error,
            R.id.bt_open_door_test,
            R.id.bt_patrol,
            R.id.bt_stop_patrol,
            R.id.bt_get_hid_usb,
            R.id.invisible_button
        )
        buttonIds.forEach { id ->
            findViewById<View>(id)?.setOnClickListener(this)
        }
        setMsg("Please create 5 locations before test!")
    }



    private fun init() {
        switchGo = SwitchGo.getInstance(this)
        switchGo.registerCallback(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnBatteryStatusChangedListener(this)
        robot.addOnRequestPermissionResultListener(this)
        scope.launch(Dispatchers.IO) {
            delay(3000)
            if (!checkTemiAllPermission()) {
                requestTemiAllPermission()
            }
        }
        requestPermissions()
        getLocations()
    }




    private fun requestTemiAllPermission() {
        val permissions = Permission.values().filter {
            Robot.getInstance()
                .checkSelfPermission(it) != Permission.GRANTED && it != Permission.UNKNOWN
        }
        if (permissions.isNotEmpty()) {
            robot.requestPermissions(ArrayList(permissions), CODE_REUQEST_TEMI_PERMISSION)
        }
    }

    @SuppressLint("SdCardPath")
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
            }

            R.id.bt_patrol -> {
                if (mapLocations.size > 1) {
                    switchGo.controllerTurnSignal(0, 1)
                    switchGo.controllerTurnSignal(1, 0)
                    robot.goTo(mapLocations[locationIndex])
                }
            }

            R.id.bt_stop_patrol -> {
                switchGo.controllerTurnSignal(0, 0)
                switchGo.controllerTurnSignal(1, 0)
                switchGo.controllerAllDoors(2, 2, 2, 2)
                robot.stopMovement()
                gate1OpenCount = 0
                gate2OpenCount = 0
                gate3OpenCount = 0
                gate4OpenCount = 0
                gateAllOpenCount = 0
            }

            R.id.bt_exit -> {
                robot.stopMovement()
                openJob?.cancel()
                finish()
            }

            R.id.bt_clear_display -> setMsg("")
            R.id.bt_hid_get_mcu1_version -> {
                scope.launch {
                    val mcu1 = this@TemiGoActivity.switchGo.getMcuVersion(0x01)
                    setMsg(mcu1)
                }
            }

            R.id.bt_hid_get_mcu2_version -> {
                scope.launch {
                    val mcu2 = this@TemiGoActivity.switchGo.getMcuVersion(0x02)
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
                    val response = this@TemiGoActivity.switchGo.getAllSwitchStates()
                    setMsg(response)
                }
            }

            R.id.get_switch_lamp -> {
                interLight = if (interLight == 1) 0 else 1
                switchGo.toggleInteriorLight(interLight)
            }

            R.id.bt_mcu_reboot -> {
                scope.launch {
                    val resp = this@TemiGoActivity.switchGo.rebootMcu(1)
                }
            }

            R.id.bt_clear_error -> {
                scope.launch {
                    this@TemiGoActivity.switchGo.clearDoorBlockAlert(0x01)
                    delay(10)
                    this@TemiGoActivity.switchGo.clearDoorBlockAlert(0x02)
                    delay(10)
                    this@TemiGoActivity.switchGo.clearDoorBlockAlert(0x03)
                    delay(10)
                    switchGo.clearDoorBlockAlert(0x04)
                }
            }
            // Note: Using the update function requires the App to have USB permissions by default, otherwise the MCU upgrade will fail.
            R.id.bt_version_update -> switchGo.updateMcuVersion(1, "/sdcard/SwitchGoTopApp.bin")
            R.id.bt_version_update2 -> switchGo.updateMcuVersion(2, "/sdcard/SwitchGoBottomApp.bin")
            R.id.bt_version_update3 -> switchGo.recoveryMcu(1, "/sdcard/SwitchGoTopApp.bin")
            R.id.bt_version_update4 -> switchGo.recoveryMcu(2, "/sdcard/SwitchGoBottomApp.bin")
            R.id.bt_open_door_test -> {
                openJob = scope.launch {
                    var index = 0
                    var openCount = 0
                    var closeCount = 0

                    while (isActive) {
                        if (index >= 200) {
                            cancel()
                            break
                        }

                        if (index % 2 == 0) {
                            this@TemiGoActivity.switchGo.controllerAllDoors(1, 1, 1, 1)
                            openCount++
                            setMsg("Opening doors for the $openCount time...")
                            delay(5000)
                            val switchToggleInfo =
                                this@TemiGoActivity.switchGo.getAllSwitchStates()
                            if (!areAllDoorsOpen(switchToggleInfo)) {
                                setMsg("Failed to open doors for the $openCount time, retrying!")
                                this@TemiGoActivity.switchGo.controllerAllDoors(1, 1, 1, 1)
                            } else {
                                setMsg("Doors opened successfully for the $openCount time!")
                            }
                        } else {
                            this@TemiGoActivity.switchGo.controllerAllDoors(2, 2, 2, 2)
                            closeCount++
                            setMsg("Closing doors for the $closeCount time...")
                            delay(5000)
                            val switchToggleInfo =
                                this@TemiGoActivity.switchGo.getAllSwitchStates()
                            if (!areAllDoorsClosed(switchToggleInfo)) {
                                setMsg("Failed to close doors for the $openCount time, retrying!")
                                this@TemiGoActivity.switchGo.controllerAllDoors(2, 2, 2, 2)
                            } else {

                                setMsg("Doors closed successfully for the $closeCount time!")
                            }
                        }
                        index++
                        delay(20000)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
//        switchGo.closeDevice()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        switchGo.removeCallback(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        robot.removeOnBatteryStatusChangedListener(this)
        robot.removeOnRequestPermissionResultListener(this)
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
        }

        if (mutableStringList.size > 20) mutableStringList.removeAt(0)
    }

    override fun onUpdateStart() {
        setMsg("Starting update")
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


    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        Log.d(
            "SwitchGo",
            "onGoToLocationStatusChanged: $location, $status, $descriptionId, $description"
        )
        when (status) {
            OnGoToLocationStatusChangedListener.START -> {}

            OnGoToLocationStatusChangedListener.CALCULATING -> {}

            OnGoToLocationStatusChangedListener.REPOSING -> {}

            OnGoToLocationStatusChangedListener.GOING -> {}

            OnGoToLocationStatusChangedListener.COMPLETE -> {
                when (locationIndex) {
                    0 -> {
                        switchGo.controllerAllDoors(1, 2, 2, 2)
                        gate1OpenCount++
                        setMsg("Open door 1 for the ${gate1OpenCount}th time")
                    }

                    1 -> {
                        switchGo.controllerAllDoors(2, 1, 2, 2)
                        gate2OpenCount++
                        setMsg("Open door 2 for the ${gate2OpenCount}th time")
                    }

                    2 -> {
                        switchGo.controllerAllDoors(2, 2, 1, 2)
                        gate3OpenCount++
                        setMsg("Open door 3 for the ${gate3OpenCount}th time")
                    }

                    3 -> {
                        switchGo.controllerAllDoors(2, 2, 2, 1)
                        gate4OpenCount++
                        setMsg("Open door 4 for the ${gate4OpenCount}th time")
                    }

                    else -> {
                        switchGo.controllerAllDoors(1, 1, 1, 1)
                        gateAllOpenCount++
                        setMsg("Open all door ${gateAllOpenCount}th time")
                    }
                }

                scope.launch(Dispatchers.IO) {
                    delay(10 * 1000)
                    this@TemiGoActivity.switchGo.controllerTurnSignal(0, 0)
                    this@TemiGoActivity.switchGo.controllerTurnSignal(0, 1)
                    switchGo.controllerAllDoors(2, 2, 2, 2)
                    delay(10 * 1000)
                    val switchToggleInfo = this@TemiGoActivity.switchGo.getAllSwitchStates()
                    Log.d("TAG", "switchToggleInfo:$switchToggleInfo")
                    try {
                        if (areAllDoorsClosed(switchToggleInfo)) {
                            gotoNextLocation()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            OnGoToLocationStatusChangedListener.ABORT -> {
                if (descriptionId != 1005) {
                    scope.launch {
                        delay(30 * 1000)
                        gotoNextLocation()
                    }
                }
            }
        }
    }

    /**
     * 四门是否全关
     */
    private fun areAllDoorsClosed(hexString: String): Boolean {
        val bytes = hexString.split(" ")
            .filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
        val doorCloseStatusOffsets = listOf(9, 11, 13, 15)
        return doorCloseStatusOffsets.all { offset ->
            offset < bytes.size && bytes[offset] == 0x01.toByte()
        }
    }


    /**
     * 四门是否全开
     */
    private fun areAllDoorsOpen(hexString: String): Boolean {
        // 将十六进制字符串转换为字节数组
        val bytes = hexString.split(" ")
            .filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }
            .toByteArray()

        // 四个门的关门限位状态偏移量（直接定位GXSC）
        val doorOpenStatusOffsets = listOf(10, 12, 14, 16) // G1SO, G2SO, G3SO, G4SO

        // 检查所有关门限位状态是否均为0x01
        return doorOpenStatusOffsets.all { offset ->
            offset < bytes.size && bytes[offset] == 0x01.toByte()
        }
    }

    private fun gotoNextLocation() {
        locationIndex = (locationIndex + 1) % mapLocations.size
        val location = mapLocations[locationIndex]

        if (locationIndex % 2 == 0) {
            switchGo.controllerTurnSignal(0, 1)
            switchGo.controllerTurnSignal(1, 0)
        } else {
            switchGo.controllerTurnSignal(1, 1)
            switchGo.controllerTurnSignal(0, 0)
        }

        robot.goTo(location)
    }

    override fun onBatteryStatusChanged(batteryData: BatteryData?) {
        batteryData.let {
            val batteryLevel: Int = it?.level ?: 0
            Log.d("onBatteryStatusChanged", "batteryLevel:$batteryLevel")
            if (0 < batteryLevel && batteryLevel <= 25) {
                switchGo.setBatteryIndicator(25)
            } else if (batteryLevel > 25 && batteryLevel <= 50) {
                switchGo.setBatteryIndicator(50)
            } else if (batteryLevel > 50 && batteryLevel <= 75) {
                switchGo.setBatteryIndicator(75)
            } else if (batteryLevel > 75 && batteryLevel <= 100) {
                switchGo.setBatteryIndicator(100)
            } else {
                switchGo.setBatteryIndicator(0)
            }
        }
    }


    override fun onRequestPermissionResult(
        permission: Permission,
        grantResult: Int,
        requestCode: Int
    ) {
        Log.d(
            "TAG",
            "permission:$permission,grantResult:$grantResult,requestCode:$requestCode"
        )
    }
}
