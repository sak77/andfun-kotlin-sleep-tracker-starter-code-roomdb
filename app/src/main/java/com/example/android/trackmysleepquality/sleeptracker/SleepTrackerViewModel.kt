/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*
import kotlin.concurrent.thread

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao, application: Application) : AndroidViewModel(application) {

    private val currentSleepNight = MutableLiveData<SleepNight?>()

    //sleep tracking is mapped to whether start and end times of current night are not equal
    private val _isSleepTracking = Transformations.map(currentSleepNight) {sleepNight: SleepNight? ->
        sleepNight != null
    }

    val isSleepTracking : LiveData<Boolean>
    get() = _isSleepTracking


    //We bind the liveData directly to the xml via databinding.
    private val allNights = database.getAllSleepNights()

    //Using transformations to set boolean hasSleepRecords
    private val _hasSleepRecords = Transformations.map(allNights) { sleepNights: List<SleepNight>? ->
        sleepNights?.size!! > 0
    }

    val hasSleepRecords : LiveData<Boolean>
        get() = _hasSleepRecords

    //the above livedata is first tranformed and then sent to the xml layout. Also remember,
    // the binding.setLifecycleOwner() method has to be set in SleepTrackerFragment. Otherwise the
    //livedata would not be able to set the values to the xml.
    val nightsString = Transformations.map(allNights) {
        sleepNight -> formatNights(sleepNight, application.resources)
    }

    //To manage all our coroutines we need a job
    val viewModelJob = Job()

    //Next we need to define the scope in which the coroutine will run. It has info about the dispatcher and the job
    val scope = CoroutineScope(Dispatchers.Main + viewModelJob)

    //We define a livedata that handles navigation in this app.
    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
    val navigateToSleepQuality : MutableLiveData<SleepNight>
    get() = _navigateToSleepQuality


    //Also we define a method which will reset the navigateToSleepQuality
    fun doneWithNavigation() {
        _navigateToSleepQuality.value = null
    }

    init {
        //Initialize currentSleepNight
        scope.launch {
            currentSleepNight.value = getTonightFromDatabase()
        }
    }


    //By using withContext(Dispatchers.IO) we are executing this coroutine on an IO thread.
    private suspend fun getTonightFromDatabase() : SleepNight? {
        //Since we want to keep this function as non-blocking we create another coroutine here
        return withContext(Dispatchers.IO) {
            var night = database.getLatestSleepNight()
            if (night?.endTimeMillis != night?.startTimeMillis) {
                night = null
            }
            night
        }
    }
    /*thread {
        currentSleepNight = SleepNight()
        database.insertSleepRecord(currentSleepNight)
    }*/

    fun onStartRecording() {
        //Since this method is being invoked by the UI, We use the ui scope defined above.
        scope.launch {
            val newNight = SleepNight()
            insert(newNight)
            currentSleepNight.value = getTonightFromDatabase()
        }
    }

    //Since this long running task is not related to the UI. We use the withContext() method instead.
    private suspend fun insert(sleepNight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insertSleepRecord(sleepNight)

        }
    }

    //Again this is invoked by UI. So it makes sense to use the ui scope so that after the coroutine is complete it returns to the ui thread
    fun onStopTracking() {
        scope.launch {
            currentSleepNight.value!!.endTimeMillis = System.currentTimeMillis()
            //Below function executes on IO thread. So this is an example of thread switching using dispatchers.
            updateSleepEndTime(currentSleepNight.value)
            _navigateToSleepQuality.value = currentSleepNight.value
        }
    }

    //Long running task with no relation to UI.
    private suspend fun updateSleepEndTime(sleepNight: SleepNight?) {
        withContext(Dispatchers.IO) {
            if (sleepNight != null) {
                database.updateSleepNight(sleepNight)
            }
        }
    }

    fun onClear() {
        scope.launch {
            clear()
            currentSleepNight.value = null
        }
        /*thread {
            database.deleteAllSleepNights()
        }*/
    }

    //
    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.deleteAllSleepNights()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()   //This will cancel all the coroutines in this job
    }
}

