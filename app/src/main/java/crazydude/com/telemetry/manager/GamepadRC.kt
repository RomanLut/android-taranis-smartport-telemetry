package crazydude.com.telemetry.manager

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.*
import kotlin.math.roundToInt

interface RCChannels {
    fun getCount() : Int
    fun get(channel : Int) : Int
    fun set(channel : Int, value : Int)
    fun isValidChannel( channel : Int ) : Boolean
    fun getChannels() : IntArray;
}

class GamepadRC : RCChannels{

    private val RC_CHANNELS_COUNT : Int = 16;
    private var rcChannels : IntArray = IntArray(RC_CHANNELS_COUNT);

    override fun getCount() : Int {
        return RC_CHANNELS_COUNT
    }

    override fun get(channelIndex : Int) : Int {
        return rcChannels[channelIndex]
    }

    override fun set(channelIndex : Int, value : Int)  {
        if ( rcChannels[channelIndex] != value ){
            rcChannels[channelIndex] = value;
            this.callback?.onChannelValueChanged( this, channelIndex, value );
        }
    }

    override fun isValidChannel(channelIndex : Int) : Boolean {
        return channelIndex >=0 && channelIndex < this.getCount();
    }

    override fun getChannels() : IntArray
    {
        return this.rcChannels;
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
        public var channelIndex : Int,  //0-based index
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
        constructor(channelIndex : Int, parm0 : Int) : super ( ButtonActionId.BUTTON_INIT_CHANNEL, channelIndex, parm0, 0 );

        override fun onInit( rcChanels : RCChannels){
            if ( !rcChanels.isValidChannel( this.channelIndex ) ) return;
            rcChanels.set( this.channelIndex, parm0 );
        }
    }

    class ButtonActionButtonSetChannel : ButtonActionBase {
        constructor(channelIndex : Int, parm0 : Int) : super ( ButtonActionId.BUTTON_SET_CHANNEL, channelIndex, parm0, 0 );

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel( this.channelIndex ) ) return;
            rcChanels.set( channelIndex, parm0 )
        }
    }

    class ButtonActionButtonToggleChannel2 : ButtonActionBase {
        constructor(channelIndex : Int, parm0: Int, parm1:Int)  : super( ButtonActionId.BUTTON_TOGGLE_CHANNEL_2, channelIndex, parm0,parm1);

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel( channelIndex ) ) return;
            if ( rcChanels.get( channelIndex ) == parm0 )
                rcChanels.set( channelIndex, parm1 )
            else
                rcChanels.set( channelIndex, parm0 );
        }
    }

    class ButtonActionButtonToggleChannel3 : ButtonActionBase {
        constructor(channelIndex : Int) : super( ButtonActionId.BUTTON_TOGGLE_CHANNEL_3, channelIndex, 0,0);

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel(channelIndex) ) return;
            if ( rcChanels.get( channelIndex) == 1000 )
                rcChanels.set( channelIndex, 1500 )
            else if ( rcChanels.get( channelIndex ) == 1500 )
                    rcChanels.set( channelIndex, 2000 )
            else
                rcChanels.set( channelIndex, 1000 )
        }
    }

    class ButtonActionButtonToggleChannel4 : ButtonActionBase {
        constructor(channelIndex : Int) : super( ButtonActionId.BUTTON_TOGGLE_CHANNEL_4, channelIndex, 0,0);

        override fun onButtonPress( rcChanels : RCChannels, keyCode : Int )  {
            if ( !rcChanels.isValidChannel( channelIndex ) ) return;
            if ( rcChanels.get( channelIndex ) == 1000 )
                rcChanels.set( channelIndex,  1333 )
            else if ( rcChanels.get( channelIndex ) == 1333 )
                rcChanels.set( channelIndex, 1666 )
            else if ( rcChanels.get( channelIndex ) == 1666 )
                rcChanels.set( channelIndex, 2000 )
            else
            rcChanels.set( channelIndex, 1000 )
        }
    }

    class ButtonActionAxisLinear : ButtonActionBase {
        constructor(channelIndex : Int, parm0 : Int) : super(ButtonActionId.AXIS_LINEAR, channelIndex, parm0, 0 );

        override fun onInit(rcChannels : RCChannels){
            if ( !rcChannels.isValidChannel( this.channelIndex ) ) return;
            rcChannels.set( this.channelIndex, parm0 );
        }

        override fun onAxis( rcChanels : RCChannels, axis : Int, value : Float )  {
            if ( !rcChanels.isValidChannel( this.channelIndex ) ) return;

            if ( value < 0 ) {
                rcChanels.set( channelIndex, (parm0 + (2000-parm0)*-value*value*value).roundToInt() );
            }
            else if ( value > 0 ) {
                rcChanels.set( channelIndex, (parm0 - (parm0 - 1000)*value*value*value).roundToInt() );
            }
            else {
                rcChanels.set( channelIndex, parm0 );
            }
        }
    }

    class ButtonActionAxisAdditive : ButtonActionBase {
        private var valueF: Float = 0.0f;
        private var axisValue : Float = 0.0f;
        private var lastUpdate = System.currentTimeMillis();
        private var scheduled : Boolean = false;

        constructor(channelIndex : Int, parm0 : Int) : super( ButtonActionId.AXIS_ADDITIVE, channelIndex, parm0, 0);

        override fun onInit(rcChannels : RCChannels){
            if ( !rcChannels.isValidChannel( this.channelIndex ) ) return;
            rcChannels.set( this.channelIndex, 1000 );
        }

        private fun update(rcChannels : RCChannels, axis : Int )  {
            if ( !rcChannels.isValidChannel( this.channelIndex ) ) return;

            var t = System.currentTimeMillis()
            var deltaT = t - this.lastUpdate;
            if ( deltaT > 10000) deltaT = 10000;
            this.lastUpdate = t;
            this.valueF += parm0 * deltaT/1000.0f * -axisValue*axisValue*axisValue;

            if ( this.valueF < 1000.0f) valueF = 1000.0f;
            if ( this.valueF > 2000.0f) valueF = 2000.0f;

            rcChannels.set( this.channelIndex, this.valueF.roundToInt())

            if ( !scheduled && Math.abs( axisValue ) > 0.01f )
            {
                scheduled = true;
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        scheduled = false;
                        update(rcChannels, axis )
                    }
                }, 10)
            }
        }

        override fun onAxis(rcChannels : RCChannels, axis : Int, value : Float )  {
            this.axisValue = value;
            update( rcChannels, axis )
        }
    }

    class ButtonActionAxisUpperPart : ButtonActionBase {
        constructor(channelIndex : Int) : super( ButtonActionId.AXIS_UPPER_PART, channelIndex, 0, 0 );

        override fun onInit( rcChanels : RCChannels){
            if ( !rcChanels.isValidChannel( this.channelIndex ) ) return;
            rcChanels.set( this.channelIndex, 1000 );
        }

        override fun onAxis( rcChanels : RCChannels, axis : Int, value : Float )  {
            if ( !rcChanels.isValidChannel( this.channelIndex ) ) return;

            if ( value < 0 ) {
                rcChanels.set( channelIndex, (1000 + 1000*-value).roundToInt() );
            }
            else {
                rcChanels.set( channelIndex, 1000 );
            }
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

    Select - arm
    Start - disarm, toggle beeper
    Right trigger Cruise
    Right bumper - Altitude hold
    Left trigger - Angle, disable Cruise and AltHold
    Left bumper - Horizon, disable Cruise and AltHold
    Left thump - pos hold
    Right thumb - RTH

    DPad down - no osd
    DPad up - mission
    DPad left - autotrim
    DPad right - autotune

    A - light On/ Off
    B - VTX Power
    X - telemetry encapsulation toggle
    Y - disable autotune, autotrim, missiong
     */
    val buttonPresets = setOf(
        //yaw
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_X, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisLinear(0, 1500))),
        //throttle
        /*
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_Y, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisUpperPart( 1))),
         */
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_Y, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisAdditive( 1, 1500))),

        //roll
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_RZ, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisLinear(2, 1500))),
        //pitch
        GamepadRC.GamepadPresetItem( ButtonOrAxis.AXIS, MotionEvent.AXIS_Z, -1.0f, 1.0f,
            arrayOf( ButtonActionAxisLinear(3, 1500))),

        //disarm/beeper
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_START, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonToggleChannel2(5-1, 1000, 1500))),

        //arm
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_SELECT, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(5-1, 2000))),

        //horizon mode, disable alt hold and cruise ( left bumper)
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_L1, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(6-1, 1500),
                        ButtonActionButtonSetChannel(7-1, 1000),
                ButtonActionButtonSetChannel(8-1, 1000))),
        //angle mode, disable alt hold and cruise  ( left trigger)
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_L2, -1.0f, 1.0f,
            arrayOf( ButtonActionInitChannel(6-1, 2000),
                        ButtonActionButtonSetChannel(6-1, 2000),
                        ButtonActionButtonSetChannel(7-1, 1000),
                ButtonActionButtonSetChannel(8-1, 1000))),
        //althold ( right bumper)
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_R1, -1.0f, 1.0f,
        arrayOf( ButtonActionButtonSetChannel(7-1, 1500),
            ButtonActionButtonSetChannel(8-1, 1000))),

        //cruise( right trigger )
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_R2, -1.0f, 1.0f,
        arrayOf( ButtonActionInitChannel(7-1, 2000),
            ButtonActionButtonSetChannel(7-1, 2000),
            ButtonActionButtonSetChannel(8-1, 1000))),

        //light or//off
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_A, -1.0f, 1.0f,
        arrayOf( ButtonActionInitChannel(11-1, 2000),
            ButtonActionButtonToggleChannel2(11-1, 1000,2000))),

        //VTX Power
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_B, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonToggleChannel3(10-1))),

        //High data rate stream, telemetry encapsulation
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_X, -1.0f, 1.0f,
            arrayOf( ButtonActionInitChannel( 14-1, 2000 ),
                ButtonActionButtonToggleChannel2(12-1, 1000, 2000),
                    ButtonActionButtonToggleChannel2(14-1, 2000, 1000))),

        //Pos hold
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_THUMBL, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(8-1, 1500))),

        //RTH
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_THUMBR, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(8-1, 2000))),

        //autotrim
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_DPAD_LEFT, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(9-1, 1200))),

        //autotune
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_DPAD_RIGHT, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(9-1, 1400))),

        //no osd
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_DPAD_DOWN, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(9-1, 1800))),

        //mission
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_DPAD_UP, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(9-1, 2000))),

        //disable autotrim/autotune/mission
        GamepadRC.GamepadPresetItem( ButtonOrAxis.BUTTON, KeyEvent.KEYCODE_BUTTON_Y, -1.0f, 1.0f,
            arrayOf( ButtonActionButtonSetChannel(9-1, 1000)))

    )

    private val buttonNames: Map<Int,String> = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to "Button A",
        KeyEvent.KEYCODE_BUTTON_B to "Button B",
        KeyEvent.KEYCODE_BUTTON_Y to "Button Y",
        KeyEvent.KEYCODE_BUTTON_X to "Button X",
        KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        KeyEvent.KEYCODE_BUTTON_START to "Start",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "Thumb Left",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "Thump Right",
        KeyEvent.KEYCODE_BUTTON_R1 to "Right Bumper",
        KeyEvent.KEYCODE_BUTTON_L1 to "Left Bumper",
        KeyEvent.KEYCODE_BUTTON_R2 to "Right Trigger",
        KeyEvent.KEYCODE_BUTTON_L2 to "Left Trigger",
        KeyEvent.KEYCODE_DPAD_UP to "DPad UP",
        KeyEvent.KEYCODE_DPAD_DOWN to "DPad DOWN",
        KeyEvent.KEYCODE_DPAD_LEFT to "DPad LEFT",
        KeyEvent.KEYCODE_DPAD_RIGHT to "DPad RIGHT"
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

    private var callback : Callback? = null;

    constructor() {
        this.initRcChannels();
    }

    public fun registerCallback( callback: Callback) {
        this.callback = callback;
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
        //Log.d("GAMEPAD","HandlebuttonPress: KeyEvent code=" + keyCode + ", name=" + this.buttonNames.get((keyCode)));

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
            /*
            Log.d("GAMEPAD","X=" + event.getAxisValue(MotionEvent.AXIS_X));
            Log.d("GAMEPAD","Y=" + event.getAxisValue(MotionEvent.AXIS_Y));
            Log.d("GAMEPAD","Z=" + event.getAxisValue(MotionEvent.AXIS_Z));
            Log.d("GAMEPAD","RZ=" + event.getAxisValue(MotionEvent.AXIS_RZ));
            Log.d("GAMEPAD","HAT_X=" + event.getAxisValue(MotionEvent.AXIS_HAT_X));
            Log.d("GAMEPAD","HAT_Y=" + event.getAxisValue(MotionEvent.AXIS_HAT_Y));
            Log.d("GAMEPAD","LT=" + event.getAxisValue(MotionEvent.AXIS_LTRIGGER));
            Log.d("GAMEPAD","RT=" + event.getAxisValue(MotionEvent.AXIS_RTRIGGER));
             */

            var DPadButton : Int = -1;
            val xaxis: Float = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val yaxis: Float = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            if ( xaxis.compareTo(-1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_LEFT
            else if (xaxis.compareTo(1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_RIGHT
            else if ( yaxis.compareTo(-1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_UP
            else if ( yaxis.compareTo(1.0f) == 0 ) DPadButton = KeyEvent.KEYCODE_DPAD_DOWN;

            if ( lastDPadButton != DPadButton) {
                lastDPadButton = DPadButton;
                if (DPadButton!=-1) this.handleButtonPress( DPadButton );
                return true;
            }

            val axes : IntArray = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);
            axes.forEach{ a -> this.handleAxis( a, event.getAxisValue( a )) }
        }

        return true;
    }

    interface Callback {
        fun onChannelValueChanged( instance: RCChannels, channelIndex : Int, channelValue : Int)
    }


}
