/*
 * Copyright 2012 Dynastream Innovations Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.dsi.ant.sample.my;

import com.dsi.ant.message.fromant.DataMessage;

public class ChannelInfo
{
    public int deviceNumber;
    public int frequency;
    public int period;

    /** Master / Slave */
    public final boolean isMaster;
    
    public byte[] broadcastData = new byte[DataMessage.LENGTH_STANDARD_PAYLOAD];
    
    public boolean error;
    private String mErrorMessage;
    
    public ChannelInfo(int deviceNumber, boolean isMaster, int initialBroadcastValue)
    {
        this.deviceNumber = deviceNumber;
        this.isMaster = isMaster;
        
     // Not actually concerned with this value, so can cast to byte and lose data without issues
        broadcastData[0] = (byte)initialBroadcastValue;
        
        error = false;
        mErrorMessage = null;
    }
    
    public void die(String errorMessage)
    {
        error = true;
        mErrorMessage = errorMessage;
    }

    public void close(String errorMessage)
    {
        mErrorMessage = errorMessage;
    }

    public String getErrorString()
    {
        return mErrorMessage;
    }

    public void setDeviceNumber(Integer deviceNumber) {
        this.deviceNumber = deviceNumber;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }
}
