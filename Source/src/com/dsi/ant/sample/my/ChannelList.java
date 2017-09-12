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

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import com.dsi.ant.channel.ChannelNotAvailableException;
import com.dsi.ant.channel.UnsupportedFeatureException;
import com.dsi.ant.sample.my.ChannelService.ChannelChangedListener;
import com.dsi.ant.sample.my.ChannelService.ChannelServiceComm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelList extends Activity {
    private static final String TAG = ChannelList.class.getSimpleName();
    
    private final String PREF_TX_BUTTON_CHECKED_KEY = "ChannelList.TX_BUTTON_CHECKED";
    private boolean mCreateChannelAsMaster;
    
    private ChannelServiceComm mChannelService;
    
    private ArrayList<String> mChannelDisplayList = new ArrayList<String>();
    private ArrayAdapter<String> mChannelListAdapter;
    private Map<Integer,Integer> mIdChannelListIndexMap = new HashMap<>();
    private int counter=0;
    private boolean mChannelServiceBound = false;
    public static EditText deviceNumber;
    private void initButtons()
    {

        //Register Master/Slave Toggle handler
        ToggleButton toggleButton_MasterSlave = (ToggleButton)findViewById(R.id.toggleButton_MasterSlave);
        toggleButton_MasterSlave.setEnabled(mChannelServiceBound);
        toggleButton_MasterSlave.setChecked(mCreateChannelAsMaster);
        toggleButton_MasterSlave.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton arg0, boolean enabled)
            {
                mCreateChannelAsMaster = enabled;
            }
        });
        
        //Register Add Channel Button handler
        Button button_addChannel = (Button)findViewById(R.id.button_AddChannel);
        button_addChannel.setEnabled(mChannelServiceBound);
        button_addChannel.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                addNewChannel(false);
            }
        });
        
        //Register Clear Channels Button handler
        Button button_clearChannels = (Button)findViewById(R.id.button_ClearChannels);
        button_clearChannels.setEnabled(mChannelServiceBound);
        button_clearChannels.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                    clearAllChannels();

            }
        });
        
    }
    
    private void initPrefs()
    {

        // Retrieves the app's current state of channel transmission mode 
        // from preferences to handle app resuming.
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        
        mCreateChannelAsMaster = preferences.getBoolean(PREF_TX_BUTTON_CHECKED_KEY, true);
        
    }
    
    private void savePrefs()
    {

        // Saves the app's current state of channel transmission mode to preferences
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        
        editor.putBoolean(PREF_TX_BUTTON_CHECKED_KEY, mCreateChannelAsMaster);
        
        editor.commit();
        
    }
    
    private void doBindChannelService()
    {

        // Binds to ChannelService. ChannelService binds and manages connection between the 
        // app and the ANT Radio Service
        Intent bindIntent = new Intent(this, ChannelService.class);
        startService(bindIntent);
        mChannelServiceBound = bindService(bindIntent, mChannelServiceConnection, Context.BIND_AUTO_CREATE);
        
        if(!mChannelServiceBound)   //If the bind returns false, run the unbind method to update the GUI
            doUnbindChannelService();


    }
    
    private void doUnbindChannelService()
    {

        if(mChannelServiceBound)
        {
            unbindService(mChannelServiceConnection);

            mChannelServiceBound = false;
        }
        
        ((Button)findViewById(R.id.button_ClearChannels)).setEnabled(false);
        ((Button)findViewById(R.id.button_AddChannel)).setEnabled(false);
        ((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(false);
        
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mChannelServiceBound = false;
        
        setContentView(R.layout.activity_channel_list);
        
        initPrefs();

        deviceNumber = (EditText) findViewById(R.id.devicenumber);

        mChannelListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, mChannelDisplayList);
        ListView listView_channelList = (ListView)findViewById(R.id.listView_channelList);
        listView_channelList.setAdapter(mChannelListAdapter);
        
        if(!mChannelServiceBound) doBindChannelService();
        
        initButtons();
        
    }
    
    public void onBack() {
        finish();
    }
    
    @Override
    public void onDestroy()
    {

        doUnbindChannelService();
        
        if(isFinishing()) 
        {
            stopService(new Intent(this, ChannelService.class));
        }

        mChannelServiceConnection = null;

        savePrefs();


        super.onDestroy();
    }

    private ServiceConnection mChannelServiceConnection = new OtherServiceConnection();

    class OtherServiceConnection implements ServiceConnection{
        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder)
        {

            mChannelService = (ChannelServiceComm) serviceBinder;
            
            // Sets a listener that handles channel events
            mChannelService.setOnChannelChangedListener(new ChannelChangedListener()
            {
                // Occurs when a channel has new info/data
                @Override
                public void onChannelChanged(final ChannelInfo newInfo)
                {
                    Integer index = mIdChannelListIndexMap.get(newInfo.deviceNumber);

                    if(null != index && index.intValue() < mChannelDisplayList.size())
                    {
                        mChannelDisplayList.set(index.intValue(), getDisplayText(newInfo));
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {

                                counter++;

                                if(newInfo.error && counter==8) {
                                    counter=0;
                                    clearAllChannels();
                                    addNewChannel(true);

                                }

                                refreshList();
                            }
                        });
                    }
                }

                // Updates the UI to allow/disallow acquiring new channels 
                @Override
                public void onAllowAddChannel(boolean addChannelAllowed) {
                    // Enable Add Channel button and Master/Slave toggle if
                    // adding channels is allowed
                    ((Button)findViewById(R.id.button_AddChannel)).setEnabled(addChannelAllowed);
                    ((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(addChannelAllowed);
                }
            });

            // Initial check when connecting to ChannelService if adding channels is allowed
            boolean allowAcquireChannel = mChannelService.isAddChannelAllowed();
            ((Button)findViewById(R.id.button_AddChannel)).setEnabled(allowAcquireChannel);
            ((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(allowAcquireChannel);

            refreshList();

        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {

            // Clearing and disabling when disconnecting from ChannelService
            mChannelService = null;
            
            ((Button)findViewById(R.id.button_ClearChannels)).setEnabled(false);
            ((Button)findViewById(R.id.button_AddChannel)).setEnabled(false);
            ((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(false);
            
        }
    };
    
    // This method is called when 'Add Channel' button is clicked
    private void addNewChannel(final boolean isReopen)
    {

        if(null != mChannelService)
        {
            List<ChannelInfo> newChannelInfo;
            try
            {
                // Telling the ChannelService to add a new channel. This method
                // in ChannelService contains code required to acquire an ANT
                // channel from ANT Radio Service.
                newChannelInfo = mChannelService.addNewChannel(isReopen);
            } catch (ChannelNotAvailableException e) {
                // Occurs when a channel is not available. Printing out the
                // stack trace will show why no channels are available.
                Toast.makeText(this, "Channel Not Available", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Channel not available", e);
                return;
            } catch (UnsupportedFeatureException e) {
                Toast.makeText(this, "Channel Not Available", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Channel not available", e);
                return;
            }

            if(null != newChannelInfo)
            {
                // Adding new channel info to the list
                addChannelToList(newChannelInfo);
                mChannelListAdapter.notifyDataSetChanged();
            }
        }
        
    }
    
    private void refreshList()
    {
        if(null != mChannelService)
        {
            ArrayList<ChannelInfo> chInfoList = mChannelService.getCurrentChannelInfoForAllChannels();

            mChannelDisplayList.clear();
            addChannelToList(chInfoList);
            mChannelListAdapter.notifyDataSetChanged();
        }
        
    }

    private void addChannelToList(List<ChannelInfo> channelInfoList)
    {
        for (ChannelInfo channelInfo : channelInfoList) {
            mIdChannelListIndexMap.put(channelInfo.deviceNumber, mChannelDisplayList.size());
            mChannelDisplayList.add(getDisplayText(channelInfo));

        }

    }
    

    private static String getDisplayText(ChannelInfo channelInfo)
    {
        String displayText = null;
        
        if(channelInfo.error)
        {
            displayText = String.format("#%-6d !:%s", channelInfo.deviceNumber, channelInfo.getErrorString());
        }
        else
        {
            if(channelInfo.isMaster)
            {
                displayText = String.format("#%-6d Tx:[%2d]", channelInfo.deviceNumber, channelInfo.broadcastData[0] & 0xFF);
            }
            else
            {
                displayText = String.format("#%-6d Rx:[%2d]", channelInfo.deviceNumber, channelInfo.broadcastData[0] & 0xFF);
            }
        }
        

        return displayText;
    }
    

    private void clearAllChannels() {

        if(null != mChannelService)
        {
            // Telling ChannelService to close all the channels
            mChannelService.clearAllChannels();

         //   mChannelDisplayList.clear();
          //  mIdChannelListIndexMap.clear();
            mChannelListAdapter.notifyDataSetChanged();
            counter=0;
        }
        
    }
}
