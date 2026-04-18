package com.deltaaim.shizuku;

/**
 * Shizuku UserService interface for DeltaAim
 * Defines the communication protocol between the app and Shizuku UserService
 */
interface IUserService {
    /**
     * Execute screencap command and return the screenshot file path
     * @return The path to the captured screenshot
     */
    String captureScreen();
    
    /**
     * Check if the service is ready
     * @return true if ready
     */
    boolean isReady();
    
    /**
     * Get service version
     * @return version string
     */
    String getVersion();
}
