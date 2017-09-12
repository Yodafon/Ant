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

import android.os.RemoteException;
import android.util.Log;
import com.dsi.ant.channel.AntChannel;
import com.dsi.ant.channel.AntCommandFailedException;
import com.dsi.ant.channel.IAntChannelEventHandler;
import com.dsi.ant.message.ChannelId;
import com.dsi.ant.message.ChannelType;
import com.dsi.ant.message.fromant.BroadcastDataMessage;
import com.dsi.ant.message.fromant.ChannelEventMessage;
import com.dsi.ant.message.fromant.MessageFromAntType;
import com.dsi.ant.message.ipc.AntMessageParcel;

import java.util.Random;

public class ChannelController
{
    // The device type and transmission type to be part of the channel ID message

    // The period and frequency values the channel will be configured to

    private static final String TAG = ChannelController.class.getSimpleName();
    
    private static Random randGen = new Random();
    
    private AntChannel mAntChannel;
    private ChannelBroadcastListener mChannelBroadcastListener;
    
    private ChannelEventCallback mChannelEventCallback = new ChannelEventCallback();

    private ChannelInfo mChannelInfo;
    
    private boolean mIsOpen;
    private byte[] lastMessage;
    private boolean linkHasSent = false;
    private boolean authHasSent = false;
    private int blockSize = 0;
    private int blockCounter = 0;

    static public abstract class ChannelBroadcastListener
    {
        public abstract void onBroadcastChanged(ChannelInfo newInfo);
    }





    public ChannelController(AntChannel antChannel, boolean isMaster, int deviceId,
            ChannelBroadcastListener broadcastListener)
    {
        mAntChannel = antChannel;
        mChannelInfo = new ChannelInfo(deviceId, isMaster, 256);
        mChannelBroadcastListener = broadcastListener;

        openChannel();
    }
    
    
    boolean openChannel()
    {
        if(null != mAntChannel)
        {
            if(mIsOpen)
            {
                Log.w(TAG, "Channel was already open");
            }
            else
            {
                /*
                 * Although this reference code sets ChannelType to either a transmitting master or a receiving slave,
                 * the standard for ANT is that channels communication is bidirectional. The use of single-direction 
                 * communication in this app is for ease of understanding as reference code. For more information and
                 * any additional features on ANT channel communication, refer to the ANT Protocol Doc found at: 
                 * http://www.thisisant.com/resources/ant-message-protocol-and-usage/
                 */
                ChannelType channelType = (ChannelType.BIDIRECTIONAL_SLAVE);

                // Channel ID message contains device number, type and transmission type. In 
                // order for master (TX) channels and slave (RX) channels to connect, they 
                // must have the same channel ID, or wildcard (0) is used.
                ChannelId channelId = new ChannelId(26583,
                        1, 5);
                
                try
                {
                    // Setting the channel event handler so that we can receive messages from ANT
                    mAntChannel.setChannelEventHandler(mChannelEventCallback);

                    // Performs channel assignment by assigning the type to the channel. Additional
                    // features (such as, background scanning and frequency agility) can be enabled
                    // by passing an ExtendedAssignment object to assign(ChannelType, ExtendedAssignment).
                    mAntChannel.assign(channelType);

                    /*
                     * Configures the channel ID, messaging period and rf frequency after assigning,
                     * then opening the channel.
                     *
                     * For any additional ANT features such as proximity search or background scanning, refer to
                     * the ANT Protocol Doc found at:
                     * http://www.thisisant.com/resources/ant-message-protocol-and-usage/
                     */


                        mAntChannel.setChannelId(channelId);
                    mAntChannel.setRfFrequency(50);
                    mChannelInfo.setFrequency(50);
                    mAntChannel.setPeriod(4096);
                    mAntChannel.disableEventBuffer();
                    mChannelInfo.setPeriod(4096);
                        mAntChannel.open();
                        mIsOpen = true;
                    Log.d(TAG, "Opened channel with device number: " + mChannelInfo.deviceNumber + " Frequency: " + 50 + "Period: " + 4096);
                } catch (RemoteException e) {
                    channelError(e);
                } catch (AntCommandFailedException e) {
                    // This will release, and therefore unassign if required
                    channelError("Open failed", e);
                }
            }
        }
        else
        {
            Log.w(TAG, "No channel available");
        }
        
        return mIsOpen;
    }

    /**
     * Implements the Channel Event Handler Interface so that messages can be
     * received and channel death events can be handled.
     */
    public class ChannelEventCallback implements IAntChannelEventHandler
    {

        private void updateData(byte[] data) {
            mChannelInfo.broadcastData = data;

            mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
        }

        @Override
        public void onChannelDeath()
        {
            // Display channel death message when channel dies
            displayChannelError("Channel Death");
        }
        
        @Override
        public void onReceiveMessage(MessageFromAntType messageType, AntMessageParcel antParcel) {


            // Switching on message type to handle different types of messages
            switch(messageType)
            {

                // If data message, construct from parcel and update channel data
                case BROADCAST_DATA:
                    // Rx Data
                    Logging.appendLog(messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    Log.v(TAG, messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    if (linkHasSent == false)
                        if (antParcel.getMessageContent()[1] == 0x43 && antParcel.getMessageContent()[2] == 0x24 && antParcel.getMessageContent()[3] == 0x00) { //link state bacon
                            //sendRequestBaconAsAck();
                            Log.v(TAG, "Link state");
                            sendLinkCommandAsAck();
                            setChannelPeriod(65535);


                            linkHasSent = true;
                        }

//                    if(antParcel.getMessageContent()[1]==0x43 && antParcel.getMessageContent()[2]==0x20 && antParcel.getMessageContent()[3]==0x02) {  //auth state bacon
//                        byte[] bytes1 = {0x44,0x09, (byte)0xFF,(byte)0xFF,0x00, 0x00,0x00,0x00};
//                    byte[] bytes2 = {0x00,0x01, 0x00,(byte)0x00, 0x00, 0x00,0x00,0x00};
//                    try {
//                        mAntChannel.burstTransfer(bytes1);
//                        Thread.sleep(500);
//                        mAntChannel.burstTransfer(bytes2);
//                        Thread.sleep(500);
//                    } catch (RemoteException e) {
//                        e.printStackTrace();
//                    } catch (AntCommandFailedException e) {
//                        e.printStackTrace();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    }

                    updateData(new BroadcastDataMessage(antParcel).getPayload());
                    break;
                case ACKNOWLEDGED_DATA:
                    // Rx Data
                    Logging.appendLog(messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    Log.v(TAG, messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
//                    updateData(new AcknowledgedDataMessage(antParcel).getPayload());
//                    try {
//                        // Setting the data to be broadcast on the next channel period
//                        mAntChannel.setBroadcastData(mChannelInfo.broadcastData);
//                    } catch (RemoteException e) {
//                        channelError(e);
//                    }
                    break;
                case CHANNEL_EVENT:
                    // Constructing channel event message from parcel
                    ChannelEventMessage eventMessage = new ChannelEventMessage(antParcel);
                    // Switching on event code to handle the different types of channel events
                    switch (eventMessage.getEventCode()) {
                        case TX: {
                            Log.v(eventMessage.getEventCode().name(), "Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod());
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            // Use old info as this is what remote device has just received
                            mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);

                            mChannelInfo.broadcastData[0]++;

                            if (mIsOpen) {
                                try {
                                    // Setting the data to be broadcast on the next channel period
                                    mAntChannel.setBroadcastData(mChannelInfo.broadcastData);
                                } catch (RemoteException e) {
                                    channelError(e);
                                }
                            }
                            break;
                        }
                        case RX_SEARCH_TIMEOUT: {
                            // TODO May want to keep searching
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            displayChannelClose("No Device Found");
                            Log.v("ChannelTimeOut", "Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod());
                            break;
                        }

                        case CHANNEL_CLOSED: {
                            break;
                        }
                        case CHANNEL_COLLISION: {
                            break;
                        }
                        case RX_FAIL: {
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            Log.v(TAG, eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));

                            break;
                        }
                        case RX_FAIL_GO_TO_SEARCH: {
                            break;
                        }
                        case TRANSFER_RX_FAILED: {
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            Log.v(TAG, eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            if (linkHasSent == false) {
//                                if(antParcel.getMessageContent()[1]==0x43 && antParcel.getMessageContent()[2]==0x24 && antParcel.getMessageContent()[3]==0x01) {  //auth state bacon

                                linkHasSent = true;
                            }
                            if (authHasSent == false) {
//                                if(antParcel.getMessageContent()[1]==0x43 && antParcel.getMessageContent()[2]==0x24 && antParcel.getMessageContent()[3]==0x01) {  //auth state bacon

                                authHasSent = true;
                            }

                            break;
                        }
                        case TRANSFER_TX_COMPLETED: {
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            Log.v(TAG, eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (authHasSent == false) {
//                                if(antParcel.getMessageContent()[1]==0x43 && antParcel.getMessageContent()[2]==0x24 && antParcel.getMessageContent()[3]==0x01) {  //auth state bacon
                                Log.v(TAG, "Auth state");
                                authHasSent = true;
                                sendAuthCommandAsAck();
                            }

                            break;
                        }
                        case TRANSFER_TX_FAILED: {
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            Log.v(TAG, eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            if (linkHasSent) {
                                linkHasSent = false;
                            }
                            if (authHasSent) {
                                authHasSent = false;
                            }
                            break;
                        }
                        case TRANSFER_TX_START: {
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            Log.v(TAG, eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            break;
                        }
                        case UNKNOWN: {
                            Logging.appendLog(eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            Log.v(TAG, eventMessage.getEventCode().name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                            break;
                        }
                    }
                    break;
                case ANT_VERSION: {
                    break;
                }
                case BURST_TRANSFER_DATA:{

                    Logging.appendLog(messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    Log.v(TAG, messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    if (antParcel.getMessageContent()[1] == 0x43 && antParcel.getMessageContent()[2] == 0x20 && antParcel.getMessageContent()[3] == 0x02) {  //transport state bacon
                        Log.v(TAG, "Transport state");
                    }
                    if (antParcel.getMessageContent()[1] == 0x44 && antParcel.getMessageContent()[2] == 0x0D && antParcel.getMessageContent()[3] == 0xFF && antParcel.getMessageContent()[4] == 0xFF) {  //transport state bacon
                        blockSize = (antParcel.getMessageContent()[8] << 16) & antParcel.getMessageContent()[7];
                    }

                    blockCounter++;


                    if (blockSize == blockCounter) {

                    }

//                    byte[] bytes1 = {0x44,0x09, (byte)0xFF,(byte)0xFF,0x00, 0x00,0x00,0x00};
//                    byte[] bytes2 = {0x00,0x01, 0x00,(byte)0x00, 0x00, 0x00,0x00,0x00};
//                    try {
//                        mAntChannel.burstTransfer(bytes1);
//                        mAntChannel.burstTransfer(bytes2);
//                    } catch (RemoteException e) {
//                        e.printStackTrace();
//                    } catch (AntCommandFailedException e) {
//                        e.printStackTrace();
//                    }
                    break;
                }
                case CAPABILITIES:
                    break;
                case CHANNEL_ID:
                    break;
                case CHANNEL_RESPONSE:
                    break;
                case CHANNEL_STATUS:
                    break;
                case SERIAL_NUMBER:
                    break;
                case OTHER:
                    Logging.appendLog(messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    Log.v(TAG, messageType.name() + ", Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(antParcel.getMessageContent()));
                    break;
            }
        }
    }

    private void sendAuthCommandAsAck() {
        //D7-64-3D-EB
        byte[] bytes = {0x44, 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};
        lastMessage = bytes;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    mAntChannel.startSendAcknowledgedData(bytes);
                    Log.v(TAG, "startSendAcknowledgedData Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(bytes));

                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (AntCommandFailedException e) {
                    e.printStackTrace();

                }

            }
        };
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void setChannelPeriod(int period) {
        try {
            mAntChannel.setPeriod(period);
            mChannelInfo.setPeriod(period);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (AntCommandFailedException e) {
            e.printStackTrace();
        }
    }

    private void sendLinkCommandAsAck() {
        byte[] bytes = {0x44, 0x02, 0x32, 0x00, 0x00, 0x00, 0x00, 0x00};
        lastMessage = bytes;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Log.v(TAG, "startSendAcknowledgedData Frequency: " + mChannelInfo.getFrequency() + ",Period: " + mChannelInfo.getPeriod() + ",Data:" + bytesToHex(bytes));
                    mAntChannel.startSendAcknowledgedData(bytes);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (AntCommandFailedException e) {
                    e.printStackTrace();

                }

            }
        };
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void setmAntChannel(AntChannel mAntChannel) {
        this.mAntChannel = mAntChannel;
    }

    public ChannelInfo getCurrentInfo()
    {
        return mChannelInfo;
    }
    
    void displayChannelError(String displayText)
    {
        mChannelInfo.die(displayText);
        mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
    }
    void displayChannelClose(String displayText)
    {
        mChannelInfo.die(displayText);
        mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
    }
    
    void channelError(RemoteException e) {
        String logString = "Remote service communication failed.";
                
        Log.e(TAG, logString);
        
        displayChannelError(logString);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }
    
    void channelError(String error, AntCommandFailedException e) {
        StringBuilder logString;
        
        if(e.getResponseMessage() != null) {
            String initiatingMessageId = "0x"+ Integer.toHexString(
                    e.getResponseMessage().getInitiatingMessageId());
            String rawResponseCode = "0x"+ Integer.toHexString(
                    e.getResponseMessage().getRawResponseCode());
            
            logString = new StringBuilder(error)
                    .append(". Command ")
                    .append(initiatingMessageId)
                    .append(" failed with code ")
                    .append(rawResponseCode);
        } else {
            String attemptedMessageId = "0x"+ Integer.toHexString(
                    e.getAttemptedMessageType().getMessageId());
            String failureReason = e.getFailureReason().toString();
            
            logString = new StringBuilder(error)
            .append(". Command ")
            .append(attemptedMessageId)
            .append(" failed with reason ")
            .append(failureReason);
        }
                
        Log.e(TAG, logString.toString());
        
        mAntChannel=null;
        
        displayChannelError("ANT Command Failed");
    }
    
    public void close()  {
        // TODO kill all our resources
        if (null != mAntChannel)
        {

            mIsOpen = false;
            
            // Releasing the channel to make it available for others. 
            // After releasing, the AntChannel instance cannot be reused.
           mAntChannel.release();

        }

        Log.d("Close:","Channel Closed");
        //displayChannelClose("Channel Closed");
    }
}
