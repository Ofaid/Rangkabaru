/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Rangkabaru ST12 - Neon Visualizer Integration
 */

package se.lublin.mumla.channel;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.CursorWrapper;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.IHumlaObserver;
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.db.DatabaseProvider;
// IMPORT NEON VISUALIZER (Sesuaikan package saat file sudah jadi)
import se.lublin.mumla.ofa.NeonVisualizerView; 
import se.lublin.mumla.util.HumlaServiceFragment;

public class ChannelListFragment extends HumlaServiceFragment 
        implements OnChannelClickListener, OnUserClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = ChannelListFragment.class.getName();

    // === NEON VISUALIZER DUAL MODE ===
    private NeonVisualizerView mNeonVisualizer;
    private int mCurrentSpeakerSession = -1;
    private boolean mIsSelfTalking = false;
    
    // Loop polling visualizer (~30fps)
    private final Runnable mVisualizerPoller = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || getView() == null || mNeonVisualizer == null) return;
            
            IHumlaService service = getService();
            if (service instanceof HumlaService) {
                HumlaService humlaService = (HumlaService) service;
                
                // LOGIKA DUAL MODE UNTUK NEON:
                if (mIsSelfTalking) {
                    // Mode MIC: Ambil data real dari service
                    float micLevel = humlaService.getMicLevel(); 
                    mNeonVisualizer.setAudioLevel(micLevel);
                } 
                else if (mCurrentSpeakerSession != -1) {
                    // Mode MONITOR: Placeholder sampai library siap kirim data teman
                    // Nanti ganti 0.5f dengan humlaService.getMonitorLevel(mCurrentSpeakerSession)
                    mNeonVisualizer.setAudioLevel(0.5f); 
                } 
                else {
                    // Hening total
                    mNeonVisualizer.setAudioLevel(0f);
                }
            }
            
            // Ulangi setiap 33ms
            getView().postDelayed(this, 33);
        }
    };

    private IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override
        public void onDisconnected(HumlaException e) {
            if (mChannelView != null) mChannelView.setAdapter(null);
            mCurrentSpeakerSession = -1;
            mIsSelfTalking = false;
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }
            
            if (getService() == null || !getService().isConnected()) return;
            
            try {
                int selfSession = getService().HumlaSession().getSessionId();
                if (user.getSession() == selfSession) {
                    scrollToChannel(newChannel.getId());
                }
            } catch (Exception e) {
                Log.d(TAG, "exception in onUserJoinedChannel: " + e);
            }
        }

        @Override public void onChannelAdded(IChannel channel) { updateList(); }
        @Override public void onChannelRemoved(IChannel channel) { updateList(); }
        @Override public void onChannelStateUpdated(IChannel channel) { updateList(); }
        @Override public void onUserConnected(IUser user) { updateList(); }
        
        @Override
        public void onUserRemoved(IUser user, String reason) {
            if (getService() != null && getService().isConnected()) updateList();
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            if (mChannelListAdapter != null && mChannelView != null) {
                mChannelListAdapter.updateUserStates(user, mChannelView);
            }
            if (getActivity() != null) getActivity().supportInvalidateOptionsMenu();
        }

        // === DETEKSI SIAPA YANG BICARA UNTUK SWITCH MODE NEON ===
        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (mChannelListAdapter != null && mChannelView != null) {
                mChannelListAdapter.updateUserStates(user, mChannelView);
            }
            
            boolean isTalking = user.getTalkState() == TalkState.TALKING 
                             || user.getTalkState() == TalkState.SHOUTING;
            
            try {
                IHumlaSession session = getService().HumlaSession();
                int mySession = session.getSessionId();
                
                if (user.getSession() == mySession) {
                    mIsSelfTalking = isTalking;
                } else if (isTalking) {
                    mCurrentSpeakerSession = user.getSession();
                    mIsSelfTalking = false;
                } else if (mCurrentSpeakerSession == user.getSession()) {
                    mCurrentSpeakerSession = -1;
                }
            } catch (Exception ignored) {}
        }
    };

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(getActivity() != null) getActivity().supportInvalidateOptionsMenu();
        }
    };

    private RecyclerView mChannelView;
    private ChannelListAdapter mChannelListAdapter;
    private ChatTargetProvider mTargetProvider;
    private DatabaseProvider mDatabaseProvider;
    private ActionMode mActionMode;
    private Settings mSettings;

    private void updateList() {
        if (mChannelListAdapter != null) {
            mChannelListAdapter.updateChannels();
            mChannelListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try { mTargetProvider = (ChatTargetProvider) getParentFragment(); } 
        catch (ClassCastException e) { throw new ClassCastException("Parent must implement ChatTargetProvider"); }
        
        try { mDatabaseProvider = (DatabaseProvider) getActivity(); } 
        catch (ClassCastException e) { throw new ClassCastException("Activity must implement DatabaseProvider"); }
        
        mSettings = Settings.getInstance(activity);
        PreferenceManager.getDefaultSharedPreferences(activity)
            .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel_list, container, false);
        mChannelView = view.findViewById(R.id.channelUsers);
        mChannelView.setLayoutManager(new LinearLayoutManager(getActivity()));
        
        // INISIALISASI NEON VISUALIZER
        mNeonVisualizer = view.findViewById(R.id.neon_visualizer_fragment);
        
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(mChannelView);
        
        // Register Bluetooth Receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getActivity().registerReceiver(mBluetoothReceiver, 
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED), RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(mBluetoothReceiver, 
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        }
        
        // MULAI LOOP NEON VISUALIZER
        if (getView() != null) {
            getView().post(mVisualizerPoller);
        }
    }

    @Override
    public void onDetach() {
        if (getActivity() != null) {
            try { getActivity().unregisterReceiver(mBluetoothReceiver); } 
            catch (IllegalArgumentException ignored) {}
        }
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        // HENTIKAN LOOP AGAR TIDAK LEAK
        if (getView() != null) getView().removeCallbacks(mVisualizerPoller);
        
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public IHumlaObserver getServiceObserver() { return mServiceObserver; }

    @Override
    public void onServiceBound(IHumlaService service) {
        try {
            if (mChannelListAdapter == null) setupChannelList();
            else mChannelListAdapter.setService(service);
        } catch (RemoteException e) { e.printStackTrace(); }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);

        if(getService() != null && getService().isConnected()) {
            IHumlaSession session = getService().HumlaSession();
            int foregroundColor = getActivity().getTheme()
                .obtainStyledAttributes(new int[]{android.R.attr.textColorPrimaryInverse})
                .getColor(0, -1);

            IUser self = session.getSessionUser();
            if (self != null) {
                muteItem.setIcon(self.isSelfMuted() ? R.drawable.ic_action_microphone_muted : R.drawable.ic_action_microphone);
                deafenItem.setIcon(self.isSelfDeafened() ? R.drawable.ic_action_audio_muted : R.drawable.ic_action_audio);
                if (muteItem.getIcon() != null) muteItem.getIcon().mutate().setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
                if (deafenItem.getIcon() != null) deafenItem.getIcon().mutate().setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
            }

            MenuItem bluetoothItem = menu.findItem(R.id.menu_bluetooth);
            bluetoothItem.setChecked(session.usingBluetoothSco());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu);
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override public boolean onSuggestionSelect(int i) { return false; }
            @Override
            public boolean onSuggestionClick(int i) {
                if (getService() == null || !getService().isConnected()) return false;
                CursorWrapper cursor = (CursorWrapper) searchView.getSuggestionsAdapter().getItem(i);
                int typeColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA);
                int dataIdColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA);
                String itemType = cursor.getString(typeColumn);
                int itemId = cursor.getInt(dataIdColumn);

                try {
                    IHumlaSession session = getService().HumlaSession();
                    if(ChannelSearchProvider.INTENT_DATA_CHANNEL.equals(itemType)) {
                        if(session.getSessionChannel().getId() != itemId) session.joinChannel(itemId);
                        else scrollToChannel(itemId);
                        return true;
                    } else if(ChannelSearchProvider.INTENT_DATA_USER.equals(itemType)) {
                        scrollToUser(itemId);
                        return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (getService() == null || !getService().isConnected()) return super.onOptionsItemSelected(item);
        IHumlaSession session = getService().HumlaSession();
        int itemId = item.getItemId();
        
        if (itemId == R.id.menu_mute_button) {
            IUser self = session.getSessionUser();
            if (self != null) {
                boolean muted = !self.isSelfMuted();
                boolean deafened = self.isSelfDeafened() && muted;
                session.setSelfMuteDeafState(muted, deafened);
            }
            getActivity().supportInvalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_deafen_button) {
            IUser self = session.getSessionUser();
            if (self != null) session.setSelfMuteDeafState(!self.isSelfDeafened(), true);
            getActivity().supportInvalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_bluetooth) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) session.enableBluetoothSco(); 
            else session.disableBluetoothSco();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupChannelList() throws RemoteException {
        mChannelListAdapter = new ChannelListAdapter(getActivity(), getService(),
                mDatabaseProvider.getDatabase(), getChildFragmentManager(),
                isShowingPinnedChannels(), mSettings.shouldShowUserCount());
        mChannelListAdapter.setOnChannelClickListener(this);
        mChannelListAdapter.setOnUserClickListener(this);
        mChannelView.setAdapter(mChannelListAdapter);
        mChannelListAdapter.notifyDataSetChanged();
    }

    public void scrollToChannel(int channelId) {
        if (mChannelListAdapter != null) {
            int pos = mChannelListAdapter.getChannelPosition(channelId);
            mChannelView.scrollToPosition(pos);
        }
    }

    public void scrollToUser(int userId) {
        if (mChannelListAdapter != null) {
            int pos = mChannelListAdapter.getUserPosition(userId);
            mChannelView.scrollToPosition(pos);
        }
    }

    private boolean isShowingPinnedChannels() {
        return getArguments() != null && getArguments().getBoolean("pinned");
    }

    @Override
    public void onChannelClick(IChannel channel) {
        if (mTargetProvider.getChatTarget() != null &&
                channel.equals(mTargetProvider.getChatTarget().getChannel()) && mActionMode != null) {
            mActionMode.finish();
        } else {
            mActionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(
                new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(channel)) {
                    @Override public void onDestroyActionMode(ActionMode am) { super.onDestroyActionMode(am); mActionMode = null; }
                });
        }
    }

    @Override
    public void onUserClick(IUser user) {
        if (mTargetProvider.getChatTarget() != null &&
                user.equals(mTargetProvider.getChatTarget().getUser()) && mActionMode != null) {
            mActionMode.finish();
        } else {
            mActionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(
                new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(user)) {
                    @Override public void onDestroyActionMode(ActionMode am) { super.onDestroyActionMode(am); mActionMode = null; }
                });
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Settings.PREF_SHOW_USER_COUNT.equals(key) && mChannelListAdapter != null) {
            mChannelListAdapter.setShowChannelUserCount(mSettings.shouldShowUserCount());
        }
    }
}