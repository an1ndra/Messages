package com.anindra.messages.ui

object PhotoDecode {
    /** Power-of-two downsample so the decoded image is about 2x the requested size. */
    fun sampleSize(width: Int, height: Int, reqPx: Int): Int {
        if (width <= 0 || height <= 0 || reqPx <= 0) return 1
        var sample = 1
        while (width / sample > reqPx * 2 || height / sample > reqPx * 2) sample *= 2
        return sample
    }
}
