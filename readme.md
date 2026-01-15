# SwitchGo API Documentation

## Overview

The SwitchGo SDK provides interfaces for communicating with SwitchGo smart devices, supporting functions such as door control, lighting control, status monitoring, and firmware updates. This document details the usage methods of all available interfaces within the SDK.

## Method Details

### 1. toggleAmbientLight

Controls the ambient light mode and color.

**Parameters:**

| Parameter Name | Type | Description                                                                                                           |
|----------------|------|-----------------------------------------------------------------------------------------------------------------------|
| mode | Int | Light mode:0 - Turn off ambient light 1 - Blinking mode 2 - Breathing mode 3 - Steady on mode 4 - Color gradient mode |
| r | Int | Red component (0-255). In color gradient mode, this parameter controls the gradient speed.                            |
| g | Int | Green component (0-255)                                                                                               |
| b | Int | Blue component (0-255)                                                                                                |

> **Note:** In color gradient mode, R/G/B values do not affect the color, but the R value can change the color gradient speed.

---

### 2. getAllSwitchStates

Gets the status of all switches and sensors.

**Return Value:** String - A hexadecimal format string containing all status information.

**Status Information Details:**

| Byte Position | Name | Description                                                                                                                                                      |
|---------------|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0x30 | - | -                                                                                                                                                                |
| 0x00 | - | -                                                                                                                                                                |
| 0x00 | - | -                                                                                                                                                                |
| 0x16 | - | -                                                                                                                                                                |
| 0x84 | - | -                                                                                                                                                                |
| G1 | Door 1 Motor Status | 0x00 - Motor stopped 0x01 - Motor powered and running 0x03 - Motor powered but not rotating 0x04 - Motor not powered but rotation detected 0xFF - Unknown status |
| G2 | Door 2 Motor Status | Same as above                                                                                                                                                    |
| G3 | Door 3 Motor Status | Same as above                                                                                                                                                    |
| G4 | Door 4 Motor Status | Same as above                                                                                                                                                    |
| G1SC | Door 1 Close Limit | 0x00 - Limit not triggered 0x01 - Limit triggered (door fully closed) 0xFF - Unknown limit status                                                                |
| G1SO | Door 1 Open Limit | 0x00 - Limit not triggered 0x01 - Limit triggered (door fully open) 0xFF - Unknown limit status                                                                  |
| G2SC | Door 2 Close Limit | Same as above                                                                                                                                                    |
| G2SO | Door 2 Open Limit | Same as above                                                                                                                                                    |
| G3SC | Door 3 Close Limit | Same as above                                                                                                                                                    |
| G3SO | Door 3 Open Limit | Same as above                                                                                                                                                    |
| G4SC | Door 4 Close Limit | Same as above                                                                                                                                                    |
| G4SO | Door 4 Open Limit | Same as above                                                                                                                                                    |
| SPACE1 | Partition 1 Status | 1 - Partition installed 2 - Partition not installed 0xFF - Unknown partition status                                                                              |
| SPACE2 | Partition 2 Status | Same as above                                                                                                                                                    |
| SPACE3 | Partition 3 Status | Same as above                                                                                                                                                    |
| SOC | Battery Level | 0-100 - Battery charge percentage 0xFF - Battery indicator module disconnected                                                                                   |
| AUXILIARY | Slave Connection Status | 0xFF - Slave disconnectedOther values - Slave connected normally                                                                                                 |
| G1R | Door 1 Blockage Alarm | 0x80 - Door close blockage alarmOther values - Normal status                                                                                                     |
| G2R | Door 2 Blockage Alarm | Same as above                                                                                                                                                    |
| G3R | Door 3 Blockage Alarm | Same as above                                                                                                                                                    |
| G4R | Door 4 Blockage Alarm | Same as above                                                                                                                                                    |

---

### 3. getMcuVersion

Gets the MCU version number.

**Parameters:**

| Parameter Name | Type | Description                                                      |
|----------------|------|------------------------------------------------------------------|
| value | Int | 1 - Get main MCU version number 2 - Get slave MCU version number |

**Return Value:** String - MCU version number string.

---

### 4. updateMcuVersion

Updates the MCU firmware.

**Parameters:**

| Parameter Name | Type | Description                              |
|----------------|------|------------------------------------------|
| value | Int | 1 - Update main MCU 2 - Update slave MCU |
| filePath | String | File path                                |

---

### 5. recoveryMcu

Recovers the MCU firmware.

**Parameters:**

| Parameter Name | Type | Description                                |
|----------------|------|--------------------------------------------|
| value | Int | 1 - Recover main MCU 2 - Recover slave MCU |
| filePath | String | Firmware file path                         |

---

### 6. controllerAllDoors

Controls the open/close status of all doors.

**Parameters:**

| Parameter Name | Type | Description                                                   |
|----------------|------|---------------------------------------------------------------|
| g1 | Int | Door 1 control: 2 - Close door 1 - Open door 0 - No operation |
| g2 | Int | Door 2 control                                                |
| g3 | Int | Door 3 control                                                |
| g4 | Int | Door 4 control                                                |

---

### 7. controllerTurnSignal

Controls the turn signal lights.

**Parameters:**

| Parameter Name | Type | Description                         |
|----------------|------|-------------------------------------|
| left | Int | Left turn signal:0 - Off 1 - Blink  |
| right | Int | Right turn signal:0 - Off 1 - Blink |

---

### 8. setBatteryIndicator

Sets the battery indicator.

**Parameters:**

| Parameter Name | Type | Description |
|----------------|------|-------------|
| value | Int | Battery level value, range 0-100 |

---

### 9. toggleInteriorLight

Controls the interior light.

**Parameters:**

| Parameter Name | Type | Description    |
|----------------|------|----------------|
| value | Int | 0 - Off 1 - On |

---

### 10. clearDoorBlockAlert

Clears the door close blockage alarm.

**Parameters:**

| Parameter Name | Type | Description                                                                                                                                             |
|----------------|------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| value | Int | 1 - Clear door 1 close blockage alarm 2 - Clear door 2 close blockage alarm 3 - Clear door 3 close blockage alarm 4 - Clear door 4 close blockage alarm |

---

### 11. rebootMcu

Reboots the MCU.

**Parameters:**

| Parameter Name | Type | Description                               |
|----------------|------|-------------------------------------------|
| value | Int | 1 - Reboot main MCU  2 - Reboot slave MCU |

**Return Value:** String - Reboot result information.

---

### 12. getHidDevices

Gets information for all HID devices.

**Return Value:** String - A string containing information for all connected HID devices.

---

### 13. openDevice

Opens the device with the specified VID and PID.

**Return Value:** Boolean - Whether the device was successfully opened.
- `true` - Device opened successfully
- `false` - Device failed to open

---

### 14. McuUpdateCallback

**Parameters:**

| Parameter Name | Type | Description |
|----------------|------|-------------|
| callback | McuUpdateCallback | Callback object that receives update progress and success/failure notifications. |

**McuUpdateCallback**
Firmware update callback interface, used to receive firmware update progress and result notifications.

---

## Precautions

1.  Suspend functions must be called within a coroutine scope.
2.  Do not disconnect the device during the firmware update process.
3.  Ensure there are no obstructions when operating doors.