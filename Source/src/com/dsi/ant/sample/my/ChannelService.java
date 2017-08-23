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

import android.nfc.Tag;
import android.os.Parcel;
import com.dsi.ant.channel.*;
import com.dsi.ant.sample.my.ChannelController.ChannelBroadcastListener;

import com.dsi.ant.AntService;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.util.*;
import java.util.stream.Stream;

public class  ChannelService extends Service
{
    private static final String TAG = "ChannelService";
    
    private Object mCreateChannel_LOCK = new Object();
    
    Map<Integer,ChannelController> mChannelControllerList = new HashMap<>();
    
    ChannelChangedListener mListener;
    
    List<Integer> availableIds=new ArrayList<>();
    private boolean mAntRadioServiceBound;
    private AntService mAntRadioService = null;
    private AntChannelProvider mAntChannelProvider = null;
    private boolean mAllowAddChannel = false;
    
    private ServiceConnection mAntRadioServiceConnection = new MyServiceConnection();
   class MyServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            // Must pass in the received IBinder object to correctly construct an AntService object
            mAntRadioService = new AntService(service);
            
            try {
                // Getting a channel provider in order to acquire channels
                mAntChannelProvider = mAntRadioService.getChannelProvider();
                
                // Initial check for number of channels available
                boolean mChannelAvailable = mAntChannelProvider.getNumChannelsAvailable() > 0;
                // Initial check for if legacy interface is in use. If the
                // legacy interface is in use, applications can free the ANT
                // radio by attempting to acquire a channel.
                boolean legacyInterfaceInUse = mAntChannelProvider.isLegacyInterfaceInUse();
                
                // If there are channels OR legacy interface in use, allow adding channels
                if(mChannelAvailable || legacyInterfaceInUse) {
                    mAllowAddChannel = true;
                }
                else {
                    // If no channels available AND legacy interface is not in use, disallow adding channels
                    mAllowAddChannel = false;
                }
                
                if(mAllowAddChannel) {
                    if(null != mListener) {
                        // Send an event that indicates if adding channels is allowed
                        mListener.onAllowAddChannel(mAllowAddChannel);
                    }
                }
                
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            die("Binder Died");
            
            mAntChannelProvider = null;
            mAntRadioService = null;
            
            if(mAllowAddChannel) { mListener.onAllowAddChannel(false); }
            mAllowAddChannel = false;
        }
        
    };
    
    public interface ChannelChangedListener
    {
        /**
         * Occurs when a Channel's Info has changed (i.e. a newly created
         * channel, channel has transmitted or received data, or if channel has
         * been closed.
         * 
         * @param newInfo The channel's updated info
         */
        void onChannelChanged(ChannelInfo newInfo);
        
        /**
         * Occurs when there is adding a channel is being allowed or disallowed.
         * 
         * @param addChannelAllowed True if adding channels is allowed. False, otherwise.
         */
        void onAllowAddChannel(boolean addChannelAllowed);
    }
    
    /**
     * The interface used to communicate with the ChannelService
     */
    public class ChannelServiceComm extends Binder
    {
        /**
         * Sets the listener to be used for channel changed event callbacks.
         * 
         * @param listener The listener that will receive events
         */
        void setOnChannelChangedListener(ChannelChangedListener listener)
        {
            mListener = listener;
        }
        
        /**
         * Retrieves the current info for all channels currently added.
         * 
         * @return A list that contains info for all the channels
         */
        ArrayList<ChannelInfo> getCurrentChannelInfoForAllChannels()
        {
            ArrayList<ChannelInfo> retList = new ArrayList<ChannelInfo>();
            for(int i = 1; i <=  mChannelControllerList.size(); i++)
            {
                ChannelController channel = mChannelControllerList.get(i);
                
                retList.add(channel.getCurrentInfo());
            }
            
            return retList;
        }
        
        /**
         * Acquires and adds a channel from ANT Radio Service
         * 
         * @param isReopen True if channel is transmitting, False if channel is receiving
         * @return The info for the newly acquired and added channel
         * @throws ChannelNotAvailableException
         */
        List<ChannelInfo> addNewChannel(final boolean isReopen) throws ChannelNotAvailableException
        {

            Log.v(TAG,"Before Add new item "+Arrays.toString(availableIds.toArray()));

            List<ChannelInfo> channelList=new ArrayList<>();
            for (Integer i : availableIds) {
            Integer id = i;
            Log.v(TAG, "Added: "+id);
            channelList.add(createNewChannel(false, id));
            }

            return channelList;
        }
        
        /**
         * Closes all channels currently added.
         */
        void clearAllChannels()  { closeAllChannels(); }
        void clearChannel(int id ) { closeChannel(id); }

        /**
         * Queries if adding a channel is allowed.
         * @return True if adding a channel is allowed. False, otherwise.
         */
        boolean isAddChannelAllowed() { return mAllowAddChannel; }
    }
    
    private void closeAllChannels() {
        synchronized (mChannelControllerList)
        {
            // Closing all channels in the list
            for(int i = 1; i <=  mChannelControllerList.size(); i++)
            {
                mChannelControllerList.get(i).close();
            }
        }
        
        // Reset the device id counter
       // doInitializeIds();
    }

    private void closeChannel(int id) {
      //  synchronized (mChannelControllerList)
        {
            // Closing all channels in the list
            for (int i = 1; i <= mChannelControllerList.size(); i++) {
                if(mChannelControllerList.get(i).getCurrentInfo().deviceNumber==id){
                    mChannelControllerList.get(i).close();
                    mChannelControllerList.remove(i);
                    Log.v(TAG,"ControllerRemoved: deviceNmber"+id+", listId"+i);
                    // Reset the device id counter
                    availableIds.add(id);
                    break;
                }
            }
            Log.v(TAG,Arrays.toString(availableIds.toArray()));
        }


    }

    AntChannel acquireChannel() throws ChannelNotAvailableException
    {
        AntChannel mAntChannel = null;
        if(null != mAntChannelProvider)
        {
            try
            {
                /*
                 * If applications require a channel with specific capabilities
                 * (event buffering, background scanning etc.), a Capabilities
                 * object should be created and then the specific capabilities
                 * required set to true. Applications can specify both required
                 * and desired Capabilities with both being passed in
                 * acquireChannel(context, PredefinedNetwork,
                 * requiredCapabilities, desiredCapabilities).
                 */
                mAntChannel = mAntChannelProvider.acquireChannel(this, PredefinedNetwork.PUBLIC);
            } catch (RemoteException e)
            {
                die("ACP Remote Ex");
            }
        }
        return mAntChannel;
    }
    
    public ChannelInfo createNewChannel(final boolean isMaster, Integer id) throws ChannelNotAvailableException
    {
        ChannelController channelController = null;
      
        synchronized(mCreateChannel_LOCK)
        {
            // Acquiring a channel from ANT Radio Service
            AntChannel antChannel = acquireChannel();
            
            if(null != antChannel)
            {
                    // Constructing a controller that will manage and control the channel
                channelController = new ChannelController(antChannel, isMaster, 0,
                        new ChannelBroadcastListener()
                {                        
                    @Override
                    public void onBroadcastChanged(ChannelInfo newInfo)
                    {
                        // Sending a channel changed event when message from ANT is received
                            mListener.onChannelChanged(newInfo);

                    }
                });

                mChannelControllerList.put(id, channelController);
            }
        }
        
        if(null == channelController) return null;
        
        return channelController.getCurrentInfo();
    }

    public ChannelInfo openNewChannel(final boolean isMaster, Integer id) throws ChannelNotAvailableException
    {
        ChannelController channelController;

        synchronized(mCreateChannel_LOCK)
        {

            // Acquiring a channel from ANT Radio Service
            AntChannel antChannel = acquireChannel();


            int newId = new Random().nextInt(200);

            channelController = mChannelControllerList.get(id);
            // Constructing a controller that will manage and control the channel
            channelController.setmAntChannel(antChannel);
            channelController.getCurrentInfo().setDeviceNumber(newId);
            channelController.openChannel();

            mChannelControllerList.put(id, channelController);
            }


       return channelController.getCurrentInfo();
    }


    @Override
    public IBinder onBind(Intent arg0)
    {
        return new ChannelServiceComm();
    }
    
    /**
     * Receives AntChannelProvider state changes being sent from ANT Radio Service
     */
    private final BroadcastReceiver mChannelProviderStateChangedReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if(AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED.equals(intent.getAction())) {
                boolean update = false;
                // Retrieving the data contained in the intent
                int numChannels = intent.getIntExtra(AntChannelProvider.NUM_CHANNELS_AVAILABLE, 0);
                boolean legacyInterfaceInUse = intent.getBooleanExtra(AntChannelProvider.LEGACY_INTERFACE_IN_USE, false);
                
                if(mAllowAddChannel) {
                    // Was a acquire channel allowed
                    // If no channels available AND legacy interface is not in use, disallow acquiring of channels
                    if(0 == numChannels && !legacyInterfaceInUse) {
                        mAllowAddChannel = false;
                        update = true;
                    }
                } else {
                    // Acquire channels not allowed
                    // If there are channels OR legacy interface in use, allow acquiring of channels
                    if(numChannels > 0 || legacyInterfaceInUse) {
                        mAllowAddChannel = true;
                        update = true;
                    }
                }
                
                if(update && (null != mListener)) {
                    // AllowAddChannel has been changed, sending event callback
                    mListener.onAllowAddChannel(mAllowAddChannel);
                }
            }
        }
    };
    
    private void doBindAntRadioService()
    {
        if(BuildConfig.DEBUG) Log.v(TAG, "doBindAntRadioService");
        
        // Start listing for channel available intents
        registerReceiver(mChannelProviderStateChangedReceiver, new IntentFilter(AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED));
        
        // Creating the intent and calling context.bindService() is handled by
        // the static bindService() method in AntService
        mAntRadioServiceBound = AntService.bindService(this, mAntRadioServiceConnection);
    }
    
    private void doUnbindAntRadioService()
    {
        if(BuildConfig.DEBUG) Log.v(TAG, "doUnbindAntRadioService");
        
        // Stop listing for channel available intents
        try{
            unregisterReceiver(mChannelProviderStateChangedReceiver);
        } catch (IllegalArgumentException exception) {
            if(BuildConfig.DEBUG) Log.d(TAG, "Attempting to unregister a never registered Channel Provider State Changed receiver.");
        }
        
        if(mAntRadioServiceBound)
        {
            try
            {
                unbindService(mAntRadioServiceConnection);
            }
            catch(IllegalArgumentException e)
            {
                // Not bound, that's what we want anyway
            }

            mAntRadioServiceBound = false;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        
        mAntRadioServiceBound = false;
        
        doBindAntRadioService();

        doInitializeIds();

    }

    private void doInitializeIds() {
        availableIds.clear();
        availableIds.add(1);
        availableIds.add(2);
        availableIds.add(3);
        availableIds.add(4);
        availableIds.add(5);
        availableIds.add(6);
        availableIds.add(7);
        availableIds.add(8);
    }

    @Override
    public void onDestroy()
    {
            closeAllChannels();
        doUnbindAntRadioService();
        mAntChannelProvider = null;
        
        super.onDestroy();
    }

    static void die(String error)
    {
        Log.e(TAG, "DIE: "+ error);
    }
    
}
