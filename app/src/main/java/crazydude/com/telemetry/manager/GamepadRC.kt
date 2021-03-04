package crazydude.com.telemetry.manager

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

interface RCChannels {
    fun getCount() : Int
    fun get(channel : Int) : Int
    fun set(channel : Int, value : Int)
    fun isValidChannel( channel : Int ) : Boolean
}

class GamepadRC : RCChannels{

    private val RC_CHANNELS_COUNT : Int = 16;
    public var rcChannels : IntArray = IntArray(RC_CHANNELS_COUNT);

    override fun getCount() : Int {
        return RC_CHANNELS_COUNT
    }

    override fun get(channel : Int) : Int {
        return rcChannels[channel]
    }

    override fun set(channel : Int, value : Int)  {
        if ( rcChannels[channel] != value ){
            rcChannels[channel] = value;
            //onRCChannelValueChange();
        }
    }

    override fun isValidChannel( channel : Int) : Boolean {
        return channel >=0 && channel < this.getCount();
    }

    enum class ButtonOrAxis(val value: Int) {
        AXIS(0),BUTTON(1)
    }

    enum class ButtonActionId(val value : Int ) {
        NONE(0),  //null
        BUTTON_INIT_CHANNEL(1),  //initialize channel to Parm0
        BUTTON_SET_CHANNEL(2),  //set channel to Parm0
        BUTTON_TOGGLE_CHANNEL_2( 3 ), //toggle channel between Parm0 and Parm1
        BUTTON_TOGGLE_CHANNEL_3( 4 ), //toggle channel between 1000, 1500, 2000
        BUTTON_TOGGLE_CHANNEL_4( 5 ), //toggle channel between 1000, 1333, 1666, 2000
        AXIS_LINEAR( 6 ), //linear mapping, with output middle at Parm0
        AXIS_UPPER_PART( 7 ), //only upper part of axis 0...1 -> 1000..2000
        AXIS_ADDITIVE( 8 ) //additive axis, with speed = Parm0 / second * axisValue
    }

    open class ButtonActionBase(
        public var actionId : ButtonActionId,
        public var channel : Int,
        public var parm0 : Int,
        public var parm1: Int
    )
    {
        open fun onInit( rcChanels : RCChannels){
        }

        open fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
        }

        //value == -1...1
        open fun onAxis( rcChanels : RCChannels, axis : Int, value : Float )  {
        }

    }

    class ButtonActionNone : ButtonActionBase {
        constructor( ) : super( ButtonActionId.NONE, 0,0 ,0 );
    }

    class ButtonActionInitChannel : ButtonActionBase {
        constructor( channel : Int, parm0 : Int) : super ( ButtonActionId.BUTTON_INIT_CHANNEL, channel, parm0, 0 );

        override fun onInit( rcChanels : RCChannels){
            if ( !rcChanels.isValidChannel( this.channel ) ) return;
            rcChanels.set( this.channel, parm0 );
        }
    }

    class ButtonActionButtonSetChannel : ButtonActionBase {
        constructor( channel : Int, parm0 : Int) : super ( ButtonActionId.BUTTON_SET_CHANNEL, channel, parm0, 0 );

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel( this.channel ) ) return;
            rcChanels.set( channel, parm0 )
        }
    }

    class ButtonActionButtonToggleChannel2 : ButtonActionBase {
        constructor(channel : Int, parm0: Int, parm1:Int)  : super( ButtonActionId.BUTTON_TOGGLE_CHANNEL_2, channel, 1000,2000);

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel( channel ) ) return;
            if ( rcChanels.get( channel ) == parm0 )
                rcChanels.set( channel, parm1 )
            else
                rcChanels.set( channel, parm0 );
        }
    }

    class ButtonActionButtonToggleChannel3 : ButtonActionBase {
        constructor(channel : Int) : super( ButtonActionId.BUTTON_TOGGLE_CHANNEL_3, channel, 0,0);

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel(channel) ) return;
            if ( rcChanels.get( channel) == 1000 )
                rcChanels.set( channel, 1500 )
            else if ( rcChanels.get( channel ) == 1500 )
                    rcChanels.set( channel, 2000 )
            else
                rcChanels.set( channel, 1000 )
        }
    }

    class ButtonActionButtonToggleChannel4 : ButtonActionBase {
        constructor(channel : Int) : super( ButtonActionId.BUTTON_TOGGLE_CHANNEL_4, channel, 0,0);

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel( channel ) ) return;
            if ( rcChanels.get( channel ) == 1000 )
                rcChanels.set( channel,  1333 )
            else if ( rcChanels.get( channel ) == 1333 )
                rcChanels.set( channel, 1666 )
            else if ( rcChanels.get( channel ) == 1666 )
                rcChanels.set( channel, 2000 )
            else
            rcChanels.set( channel, 1000 )
        }
    }

    class ButtonActionAxisLinear : ButtonActionBase {
        constructor( channel : Int, parm0 : Int) : super(ButtonActionId.AXIS_LINEAR, channel, parm0, 0 );

        override fun onAxis( rcChanels : RCChannels, axis : Int, value : Float )  {

        }
    }

    class ButtonActionAxisAdditive : ButtonActionBase {
        constructor( channel : Int, parm0 : Int) : super( ButtonActionId.AXIS_ADDITIVE, channel, parm0, 0);

        override fun onAxis( rcChanels : RCChannels, axis : Int, value : Float )  {

        }
    }

    class ButtonActionAxisUpperPart : ButtonActionBase {
        constructor( channel : Int) : super( ButtonActionId.AXIS_UPPER_PART, channel, 0, 0 );

        override fun onAxis( rcChanels : RCChannels, axis : Int, value : Float )  {

        }
    }

    class GamepadPresetItem(
        val isButton: ButtonOrAxis,  //is it button or axis
        val code: Int,  //either button or axis code

        //relevant for axes only
        val calibratedMin : Float = -1.0f,  //calibrated minimum value of axis
        val calibratedMax : Float = 1.0f,  //calibrated maximum value of axis

        val actions : Array<ButtonActionBase> = arrayOf(
            ButtonActionNone(),
            ButtonActionNone(),
            ButtonActionNone())
    ) {

        public fun onButtonPress( rcChannels : RCChannels, keyCode : Int ) : Boolean  {
            if ( isButton != ButtonOrAxis.BUTTON ) return false;
            if ( code != keyCode) return false;
            var handled = false;
            for ( a in actions)
                a.onButtonPress( rcChannels, keyCode)
            return true;
        }

        public fun onAxis( rcChannels: RCChannels, axis : Int, value : Float) {
            if ( isButton != ButtonOrAxis.AXIS ) return;
            if ( code != axis ) return;
            for ( a in actions)
                a.onAxis( rcChannels, axis, value );
        }
    }

    /*
    Left stick : Throttle Upper Part, Yaw
    Right stick: Roll, Pitch

    Start - Arm toggle
    Select - disarm, beeper on
    Right trigger Cruise
    Right bumper - Altitude hold
    Left trigger - Angle, disable Cruise and AltHold
    Left bumper - Horizon, disable Cruise and AltHold
    Left thump - pos hold
    Right thumb - RTH

    A - light On/ Off
    B - VTX Power
    X - off / autotrim / autotune
    Y - off / mission
     */
    val buttonPresets = setOf(
        //yaw
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_X, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisLinear(0, 1500))),
        //throttle
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_Y, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisUpperPart( 1))),
        //roll
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_RZ, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisLinear(2, 1500))),
        //pitch
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_Z, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisLinear(3, 1500))),

        //arm toggle
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_START, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonToggleChannel2(5, 1000,2000))),

        //disarm,beeper enable
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_SELECT, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(5, 1500))),

        //horizon mode, disable alt hold and cruise ( right bumper)
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_L1, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(6, 1500),
                        ButtonActionButtonSetChannel(7, 1000))),
        //angle mode, disable alt hold and cruise ( right trigger )
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_L2, -1.0f, 1.0f,
            arrayOf( ButtonActionInitChannel(6, 2000),
                        ButtonActionButtonSetChannel(6, 2000),
                        ButtonActionButtonSetChannel(7, 1000))),
        //alt hold ( right bumper)
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_L2, -1.0f, 1.0f,
        arrayOf( ButtonActionButtonSetChannel(7, 1500))),
        //cruise ( right trigger)
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_L1, -1.0f, 1.0f,
        arrayOf( ButtonActionInitChannel(7, 2000),
            ButtonActionButtonSetChannel(7, 1500))),

        //light or//off
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_A, -1.0f, 1.0f,
        arrayOf( ButtonActionInitChannel(11, 2000),
            ButtonActionButtonToggleChannel2(11, 1000,2000))),

        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_B, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonToggleChannel3(12))),

        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_X, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonToggleChannel3(9))),

        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_Y, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonToggleChannel2(13, 1000,1900)))
    )

    private val KEYCODE_DPAD_UP : Int = 1000;
    private val KEYCODE_DPAD_DOWN : Int = 1001;
    private val KEYCODE_DPAD_LEFT : Int = 1002;
    private val KEYCODE_DPAD_RIGHT : Int = 1003;

    private val buttonNames: Map<Int,String> = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to "Button A",
        KeyEvent.KEYCODE_BUTTON_X to "Button X",
        KeyEvent.KEYCODE_BUTTON_Y to "Button Y",
        KeyEvent.KEYCODE_BUTTON_X to "Button X",
        KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        KeyEvent.KEYCODE_BUTTON_START to "Start",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "Thumb Left",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "Thump Right",
        KeyEvent.KEYCODE_BUTTON_R1 to "Right Bumper",
        KeyEvent.KEYCODE_BUTTON_L1 to "Left Bumper",
        KeyEvent.KEYCODE_BUTTON_R2 to "Right Trigger",
        KeyEvent.KEYCODE_BUTTON_L1 to "Left Trigger",
        KEYCODE_DPAD_UP to "DPad UP",
        KEYCODE_DPAD_DOWN to "DPad DOWN",
        KEYCODE_DPAD_LEFT to "DPad LEFT",
        KEYCODE_DPAD_RIGHT to "DPad RIGHT"
    );

    public fun getButtonName( code: Int ) : String {
        return this.buttonNames.get(code) ?: ("Button Code: " + code);
    }

    private val axisNames: Map<Int,String> = mapOf(
        MotionEvent.AXIS_X to "Axis X",
        MotionEvent.AXIS_Y to "Axis Y",
        MotionEvent.AXIS_Z to "Axis Z",
        MotionEvent.AXIS_RZ to "Axis RZ"
    );

    public fun getAxisName( code: Int ) : String {
        return this.axisNames.get(code) ?: ("Axis Code: " + code);
    }

    constructor( ) {
        this.initRcChannels();
    }


    private fun initRcChannels()  {
        for ( i in 0..RC_CHANNELS_COUNT-1) {
            rcChannels[i] = 1000;
        }

        for ( bp in this.buttonPresets){
            for ( action in bp.actions) {
                action.onInit( this);
            }
        }
    }

    private fun handleButtonPress( keyCode : Int) : Boolean {
        Log.d("GAMEPAD","HandlebuttonPress: KeyEvent code=" + keyCode);

        var handled = false;
        for ( bp in this.buttonPresets)
            handled = handled || bp.onButtonPress( this, keyCode );
        return handled;
    }

    private fun handleAxis( axis : Int, value : Float ) {
        for ( bp in this.buttonPresets)
            bp.onAxis( this, axis, value );
    }

    //listen gamepad buttons
    public fun handleKeyEvent (event: KeyEvent) : Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) {
            if ( event.action== KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    return this.handleButtonPress( event.keyCode );
                }
            }

        return this.buttonNames.containsKey( event.keyCode);
    }

    private var lastDPadButton = -1;

    //listen gamepad sticks and d-pad
    public fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            Log.d("GAMEPAD","X=" + event.getAxisValue(MotionEvent.AXIS_X));
            Log.d("GAMEPAD","Y=" + event.getAxisValue(MotionEvent.AXIS_Y));
            Log.d("GAMEPAD","Z=" + event.getAxisValue(MotionEvent.AXIS_Z));
            Log.d("GAMEPAD","RZ=" + event.getAxisValue(MotionEvent.AXIS_RZ));
            Log.d("GAMEPAD","HAT_X=" + event.getAxisValue(MotionEvent.AXIS_HAT_X));
            Log.d("GAMEPAD","HAT_Y=" + event.getAxisValue(MotionEvent.AXIS_HAT_Y));

            var DPadButton : Int = -1;
            val xaxis: Float = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val yaxis: Float = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            if ( xaxis.compareTo(-1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_DOWN_LEFT
            else if (xaxis.compareTo(1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_RIGHT
            else if ( yaxis.compareTo(-1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_UP
            else if ( yaxis.compareTo(1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_DOWN;

            if ( lastDPadButton != DPadButton) {
                lastDPadButton = DPadButton;
                return this.handleButtonPress( DPadButton );
            }

            val axes : IntArray = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);
            axes.forEach{ a -> this.handleAxis( a, event.getAxisValue( a )) }
        }


        return false;
    }




}
