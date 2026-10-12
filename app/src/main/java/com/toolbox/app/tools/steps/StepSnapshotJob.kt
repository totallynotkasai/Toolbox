package com.toolbox.app.tools.steps

import android.app.job.JobParameters
import android.app.job.JobService
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat

/**
 * Briefly listens to the step counter, records one reading in [StepStore], and stops. Runs hourly and just after
 * midnight (see [StepScheduler]). Gives up after [TIMEOUT_MS] if the sensor stays silent.
 *
 * Every job of this service runs on the same instance, and after Doze the hourly and midnight jobs often start
 * together, so each running job keeps its own listener, keyed by job id, and its own timeout, tagged with that
 * listener.
 */
class StepSnapshotJob : JobService() {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableMapOf<Int, SensorEventListener>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (params.jobId != StepScheduler.JOB_PERIODIC) StepScheduler.scheduleNextMidnight(this, params.jobId)
        if (!StepScheduler.hasPermission(this)) return false
        val manager = getSystemService(SensorManager::class.java) ?: return false
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false

        stopListening(params.jobId) // A job the system restarted before its old run finished.
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = finish(params, this, e.values[0].toLong())
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        listeners[params.jobId] = l
        if (!manager.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            listeners.remove(params.jobId)
            return false
        }
        HandlerCompat.postDelayed(handler, { finish(params, l, null) }, l, TIMEOUT_MS)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopListening(params.jobId)
        // Retry a cut-short midnight job; the hourly one will simply run again next hour.
        return params.jobId != StepScheduler.JOB_PERIODIC
    }

    override fun onDestroy() {
        listeners.keys.toList().forEach(::stopListening)
        super.onDestroy()
    }

    private fun finish(params: JobParameters, listener: SensorEventListener, raw: Long?) {
        // Already finished: a late event after the timeout, or vice versa, or a run the system has since restarted.
        if (listeners[params.jobId] !== listener) return
        stopListening(params.jobId)
        if (raw != null) StepStore(this).record(raw)
        jobFinished(params, false)
    }

    /** Stops job [jobId]'s listener and timeout, if it still has them. */
    private fun stopListening(jobId: Int) {
        val l = listeners.remove(jobId) ?: return
        handler.removeCallbacksAndMessages(l)
        getSystemService(SensorManager::class.java)?.unregisterListener(l)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
