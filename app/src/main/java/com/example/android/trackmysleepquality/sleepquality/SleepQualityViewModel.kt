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

package com.example.android.trackmysleepquality.sleepquality

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import kotlinx.coroutines.*


class SleepQualityViewModel(val sleepDatabaseDao: SleepDatabaseDao, val sleepNightKey : Long) : ViewModel() {


    //This viewmodel will cover following -

    //Update sleep quality for given nightID in the DB
    //For this we need a job and a uiscope
    val viewModelJob = Job()

    val uiscope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    //Navigate back to sleeptracker fragment
    //For this we set a navigate livedata
    private val _navigateToSleepTracker = MutableLiveData<Boolean?>()
    val navigateToSleepTracker : MutableLiveData<Boolean?>
        get() = _navigateToSleepTracker

    //Method to update sleep quality
    fun updateSleepQuality(sleepQuality : Int) {
        uiscope.launch {
            //Switch to IO thread
            withContext(Dispatchers.IO) {
                //Get current night
                val currentNight = sleepDatabaseDao.getSleepNightById(sleepNightKey)
                currentNight?.sleepQuality = sleepQuality
                //updateDB
                if (currentNight != null) {
                    sleepDatabaseDao.updateSleepNight(currentNight)
                }
            }
            _navigateToSleepTracker.value = true
        }
    }

    fun doneWithNavigation() {
        _navigateToSleepTracker.value = false
    }
}
