/**
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Left It Unlocked
 *
 *  Author: Neil Walker
 *  Date: 2017-11-26
 */
definition(
    name: "Left It Unlocked",
    namespace: "nilswalk",
    author: "Neil Walker",
    description: "Notifies you when you have left a lock unlocked longer that a specified amount of time, and tries to lock it.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/bon-voyage.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/bon-voyage%402x.png"
)

preferences {

  section("Monitor this lock") {
    input "theLock", "capability.lock"
  }

  section("Notify me and lock it if it's open for more than this many minutes (default 10)") {
    input "lockTimeout", "number", description: "Number of minutes", required: false
  }

  section("Delay between successive attempts (default 10 minutes") {
    input "frequency", "number", title: "Number of minutes", description: "", required: false
  }

  section("Via text message at this number (or via push notification if not specified") {
    input("recipients", "contact", title: "Send notifications to") {
      input "phone", "phone", title: "Phone number (optional)", required: false
    }
  }
}

def installed() {
  log.trace "installed()"
  subscribe()
}

def updated() {
  log.trace "updated()"
  unsubscribe()
  subscribe()
}

def subscribe() {
  subscribe(theLock, "lock.unlocked", lockUnlocked)
  subscribe(theLock, "lock.locked", lockLocked)
}

def lockUnlocked(evt) {
  log.trace "lockUnlocked($evt.name: $evt.value)"
  def delay = (lockTimeout != null && lockTimeout != "") ? lockTimeout * 60 : 600
  runIn(delay, lockUnlockedTooLong, [overwrite: true])
}

def lockLocked(evt) {
  log.trace "lockLocked($evt.name: $evt.value)"
  unschedule(lockUnlockedTooLong)
}

def lockUnlockedTooLong() {
  def lockState = theLock.currentState("lock")
  def freq = (frequency != null && frequency != "") ? frequency * 60 : 600

  if (lockState.value == "unlocked") {
    def elapsed = now() - lockState.rawDateCreated.time
    def threshold = ((lockTimeout != null && lockTimeout != "") ? lockTimeout * 60000 : 60000) - 1000
    if (elapsed >= threshold) {
      log.debug "Lock has stayed unlocked long enough since last check ($elapsed ms):  calling sendMessage()"
      sendMessage()
      runIn(freq, lockUnlockedTooLong, [overwrite: false])
      lockIt()
    } else {
      log.debug "Lock has not stayed unlocked long enough since last check ($elapsed ms):  doing nothing"
    }
  } else {
    log.warn "lockUnlockedTooLong() called but contact is closed:  doing nothing"
  }
}

void sendMessage() {
  def minutes = (lockTimeout != null && lockTimeout != "") ? lockTimeout : 10
  def msg = "${theLock.displayName} has been left open for ${minutes} minutes, locking it."
  log.info msg
  if (location.contactBookEnabled) {
    sendNotificationToContacts(msg, recipients)
  } else {
    if (phone) {
      sendSms phone, msg
    } else {
      sendPush msg
    }
  }
}

void lockIt() {
	theLock.lock()
}