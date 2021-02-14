package crazydude.com.telemetry.manager

import android.content.Context
import android.view.MotionEvent

class GamepadRC(context: Context) {

    enum class ButtonOrAxis(val value: Int) {
        AXIS(0),BUTTON(1)
    }

    enum class Action(val value) {
        BUTTON_SET_CHANNEL(0),  //set channel to Parm0
        BUTTON_TOGGLE_CHANEL( 1 ), //toggle channel between Parm0 and Parm 1
        AXIS_LINEAR( 2 ), //linear mapping, with output middle at Parm0
        AXIS_UPPER_PART( 3 ), //only upper part of axis 0...1 -> 1000..2000
        AXIS_ADDITIVE( 4 ) //additive axis, with speed = Parm0 / second * axisValue
    }

    data class ActionPreset(
        val isButton: ButtonOrAxis,  //is it button or axis
        val code: Int,  //either button or axis code

        //relevant for axes only
        val calibratedMin : Float = -1.0f,  //calibrated minimum value of axis
        val calibratedMax : Float = 1.0f,  //calibrated maximum value of axis

        val action : Action,

        val parm0 : Int,
        val parm1 : Int
    )

    companion object {
        val actions = setOf(
            GamepadRC.ActionPreset( ButtonOrAxis.AXIS, MotionEvent.AXIS_X, -1.0f, 1.0f, Action.AXIS_LINEAR, 1500, 0 ),  //yaw
            GamepadRC.ActionPreset( ButtonOrAxis.AXIS, MotionEvent.AXIS_Y, -1.0f, 1.0f, Action.AXIS_UPPER_PART, 0, 0 ),  //throttle
            GamepadRC.ActionPreset( ButtonOrAxis.AXIS, MotionEvent.AXIS_RZ, -1.0f, 1.0f, Action.AXIS_LINEAR, 0, 0 ),  //roll
            GamepadRC.ActionPreset( ButtonOrAxis.AXIS, MotionEvent.AXIS_Z, -1.0f, 1.0f, Action.AXIS_LINEAR, 0, 0 ),  //pitch
        )
    }


}
