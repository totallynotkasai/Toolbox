package com.toolbox.app.tools.steps

import android.app.job.JobParameters
import android.app.job.JobService
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Briefly listens to the step counter, records one reading in [StepStore], and stops. Runs hourly and just after
 * midnight (see [StepScheduler]). Gives up after [TIMEOUT_MS] if the sensor stays silent.
 */
class StepSnapshotJob : JobService() {
    private val handler = Handler(Looper.getMainLooper())
    private var listener: SensorEventListener? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (params.jobId != StepScheduler.JOB_PERIODIC) StepScheduler.scheduleNextMidnight(this, params.jobId)
        if (!StepScheduler.hasPermission(this)) return false
        val manager = getSystemService(SensorManager::class.java) ?: return false
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false

        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = finish(params, e.values[0].toLong())
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        if (!manager.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            listener = null
            return false
        }
        handler.postDelayed({ finish(params, null) }, TIMEOUT_MS)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopListening()
        // Retry a cut-short midnight job; the hourly one will simply run again next hour.
        return params.jobId != StepScheduler.JOB_PERIODIC
    }

    private fun finish(params: JobParameters, raw: Long?) {
        if (listener == null) return // Already finished (a late event after the timeout, or vice versa).
        stopListening()
        if (raw != null) StepStore(this).record(raw)
        jobFinished(params, false)
    }

    private fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        listener?.let { getSystemService(SensorManager::class.java)?.unregisterListener(it) }
        listener = null
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
