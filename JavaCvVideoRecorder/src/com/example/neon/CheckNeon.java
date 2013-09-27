package com.example.neon;


public class CheckNeon 
{
	/* this is used to load the 'checkneon' library on application
     * startup. The library has already been unpacked into
     * /data/data/com.example.neon/lib/libcheckneon.so at
     * installation time by the package manager.
     */
    static {
        System.loadLibrary("checkneon");
    }
    /* A native method that is implemented by the
     * 'checkneon' native library, which is packaged
     * with this application.
     */
    public native static int  checkNeonFromJNI();
}
