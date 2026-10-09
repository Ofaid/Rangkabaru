/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Rangkabaru ST12 - VisualizerView Asli OFAID Integration + Realtime Decay Fixed
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
import android.os.Handler;
import android.os.Looper;
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

import java.util.Arrays;

import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.IHumlaObserver;
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.db.DatabaseProvider;
// IMPORT VISUALIZER ASLI OFAID
import ofaid.ahmad.ptt.ofa.VisualizerView; 
import se.lublin.mumla.util.HumlaServiceFragment;

public class ChannelListFragment extends HumlaServiceFragment 
        implements OnChannelClickListener, OnUserClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = ChannelListFragment.class.getName();

    // === VISUALIZER MONITOR (ASLI OFAID) ===
    private VisualizerView mVisualMonitor;
    
    // Receiver untuk menerima data byte array dari Service
    private BroadcastReceiver mPenerimaMonitor;

    // === VARIABEL UNTUK REALTIME DECAY (JANTUNG ANIMASI) ===
    private float mMonitorLevel = 0f;
    private Handler mMonitorHandler = new Handler(Looper.getMainLooper());
    private Runnable mMonitorDecayRunnable;
    
    // Threshold agar tidak "berdetak" saat hening total
    private static final float SILENCE_THRESHOLD = 0.015f; 

    private IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override
        public void onDisconnected(HumlaException e) {
            if (mChannelView != null) mChannelView.setAdapter(null);
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

        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (mChannelListAdapter != null && mChannelView != null) {
                mChannelListAdapter.updateUserStates(user, mChannelView);
            }
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
        
        // INISIALISASI VISUALIZER ASLI OFAID
        mVisualMonitor = view.findViewById(R.id.visualizerMonitor);
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 1. SETUP RECEIVER HANYA UNTUK MENERIMA DATA ATTACK
        mPenerimaMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("st12.ACTION_MONITOR_BYTES".equals(intent.getAction())) {
                    byte[] data = intent.getByteArrayExtra("bytes");
                    if (data != null && data.length > 0) {
                        // Ambil nilai pertama sebagai representasi level suara teman
                        float rawLevel = Math.abs(data[0]) / 127f;
                        
                        // BOOST SENSITIVITAS MONITOR (3.5x - 5x)
                        // Data voice dari jaringan biasanya kecil, perlu di-boost agar讲话 keras bisa full bar
                        float targetLevel = Math.min(rawLevel * 4.0f, 1.0f); 
                        
                        // Attack Cepat: Langsung naik saat ada suara baru
                        if (targetLevel > mMonitorLevel) {
                            mMonitorLevel = targetLevel;
                        }
                    }
                }
            }
        };
        requireContext().registerReceiver(mPenerimaMonitor, 
            new IntentFilter("st12.ACTION_MONITOR_BYTES"));

        // 2. JALANKAN HEARTBEAT DECAY MANDIRI SETIAP 30MS
        mMonitorDecayRunnable = new Runnable() {
            @Override
            public void run() {
                // LOGIKA DECAY YANG LEBIH PINTAR
                // Jika level di bawah threshold, paksa jadi 0 (biar gak berdetak sendiri)
                if (mMonitorLevel <= SILENCE_THRESHOLD) {
                    mMonitorLevel = 0f;
                } else {
                    // Turunkan level secara konstan agar gerakan mulus
                    mMonitorLevel *= 0.88f; // Faktor decay (0.88 = turun elegan ~250ms)
                }
                
                // Gambar ulang visualizer berdasarkan level terkini
                if (mVisualMonitor != null) {
                    byte[] smoothData = new byte[32];
                    byte val = (byte)(mMonitorLevel * 127);
                    Arrays.fill(smoothData, val);
                    mVisualMonitor.updateVisualizer(smoothData);
                }
                
                // Ulangi loop dalam 30ms (~33 FPS)
                mMonitorHandler.postDelayed(this, 30);
            }
        };
        mMonitorHandler.post(mMonitorDecayRunnable);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(mChannelView);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getActivity().registerReceiver(mBluetoothReceiver, 
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED), RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(mBluetoothReceiver, 
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
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
    public void onDestroyView() {
        super.onDestroyView();
        
        // HENTIKAN HEARTBEAT AGAR TIDAK BOROS BATERAI SAAT KELUAR FRAGMENT
        if (mMonitorHandler != null && mMonitorDecayRunnable != null) {
            mMonitorHandler.removeCallbacks(mMonitorDecayRunnable);
        }
        
        // UNREGISTER RECEIVER AGAR TIDAK LEAK
        if (mPenerimaMonitor != null) {
            try { requireContext().unregisterReceiver(mPenerimaMonitor); } 
            catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void onDestroy() {
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