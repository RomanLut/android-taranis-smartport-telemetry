package com.serenegiant.usbcameratest4;

public interface CameraFragmentListener {
    void onUsbDeviceAttached();
    void onCameraConnected();
    void onCameraConnecting();
}

