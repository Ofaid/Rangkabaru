/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Rangkabaru ST12 - Single Visualizer Dual Mode (Mic + Monitor)
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

// SATU VISUALIZER UNTUK DUAL MODE
import ofaid.ahmad.ptt.ofa.VisualizerView; 
import se.lublin.mumla.util.HumlaServiceFragment;

public class ChannelListFragment extends HumlaServiceFragment 
        implements OnChannelClickListener, OnUserClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = ChannelListFragment.class.getName();

    // SATU INSTANCE VISUALIZER UNTUK MIC DAN MONITOR
    private VisualizerView mVisualizer;
    
    // SATU RECEIVER PINTAR YANG MENANGANI KEDUA ACTION
    private BroadcastReceiver mPenerimaUniversal;

    private IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override public void onDisconnected(HumlaException e) { if (mChannelView != null) mChannelView.setAdapter(null); }
        @Override public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) { updateList(); }
        @Override public void onChannelAdded(IChannel channel) { updateList(); }
        @Override public void onChannelRemoved(IChannel channel) { updateList(); }
        @Override public void onChannelStateUpdated(IChannel channel) { updateList(); }
        @Override public void onUserConnected(IUser user) { updateList(); }
        @Override public void onUserRemoved(IUser user, String reason) { if (getService() != null && getService().isConnected()) updateList(); }
        @Override public void onUserStateUpdated(IUser user) { 
            if (mChannelListAdapter != null && mChannelView != null) mChannelListAdapter.updateUserStates(user, mChannelView);
            if (getActivity() != null) getActivity().supportInvalidateOptionsMenu();
        }
        @Override public void onUserTalkStateUpdated(IUser user) { 
            if (mChannelListAdapter != null && mChannelView != null) mChannelListAdapter.updateUserStates(user, mChannelView);
        }
    };

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if(getActivity() != null) getActivity().supportInvalidateOptionsMenu(); }
    };

    private RecyclerView mChannelView;
    private ChannelListAdapter mChannelListAdapter;
    private ChatTargetProvider mTargetProvider;
    private DatabaseProvider mDatabaseProvider;
    private ActionMode mActionMode;
    private Settings mSettings;

    private void updateList() {
        if (mChannelListAdapter != null) { mChannelListAdapter.updateChannels(); mChannelListAdapter.notifyDataSetChanged(); }
    }

    @Override public void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); setHasOptionsMenu(true); }

    @Override public void onAttach(Activity activity) {
        super.onAttach(activity);
        try { mTargetProvider = (ChatTargetProvider) getParentFragment(); } catch (ClassCastException e) { throw new ClassCastException("Parent must implement ChatTargetProvider"); }
        try { mDatabaseProvider = (DatabaseProvider) getActivity(); } catch (ClassCastException e) { throw new ClassCastException("Activity must implement DatabaseProvider"); }
        mSettings = Settings.getInstance(activity);
        PreferenceManager.getDefaultSharedPreferences(activity).registerOnSharedPreferenceChangeListener(this);
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel_list, container, false);
        mChannelView = view.findViewById(R.id.channelUsers);
        mChannelView.setLayoutManager(new LinearLayoutManager(getActivity()));
        
        // INISIALISASI SATU VISUALIZER DUAL MODE
        // GANTI ID INI SESUAI DENGAN XML KAMU (bisa visualizerMonitor atau id lain)
        mVisualizer = view.findViewById(R.id.visualizerMonitor); 
        
        return view;
    }

    @Override public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // ============================================================
        // RECEIVER UNIVERSAL: MENANGANI MIC & MONITOR DALAM SATU ALUR
        // ============================================================
        mPenerimaUniversal = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                // MODE 1: MIC SENDIRI (MENERIMA FLOAT LEVEL)
                if ("ofaid.ahmad.ptt.LEVEL_SUARA".equals(action)) {
                    float level = intent.getFloatExtra("level", 0f);
                    if (mVisualizer != null) {
                        // Konversi float mic ke byte[32] agar kompatibel dengan VisualizerView
                        byte[] micData = new byte[32];
                        byte val = (byte)(level * 127);
                        Arrays.fill(micData, val);
                        mVisualizer.updateVisualizer(micData);
                    }
                }
                
                // MODE 2: MONITOR TEMAN (MENERIMA BYTE ARRAY PCM)
                else if ("st12.ACTION_MONITOR_BYTES".equals(action)) {
                    byte[] data = intent.getByteArrayExtra("bytes");
                    if (data != null && data.length > 0 && mVisualizer != null) {
                        // LANGSUNG GAMBAR DATA PCM MURNI DARI AUDIOOUTPUT
                        mVisualizer.updateVisualizer(data);
                    } else {
                        // MATI TOTAL SAAT TIDAK ADA SUARA TEMAN
                        if (mVisualizer != null) mVisualizer.updateVisualizer(new byte[32]);
                    }
                }
            }
        };
        
        // DAFTARKAN KEDUA ACTION KE SATU RECEIVER
        IntentFilter filter = new IntentFilter();
        filter.addAction("ofaid.ahmad.ptt.LEVEL_SUARA");
        filter.addAction("st12.ACTION_MONITOR_BYTES");
        requireContext().registerReceiver(mPenerimaUniversal, filter);
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(mChannelView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getActivity().registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED), RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        }
    }

    @Override public void onDetach() {
        if (getActivity() != null) { try { getActivity().unregisterReceiver(mBluetoothReceiver); } catch (IllegalArgumentException ignored) {} }
        super.onDetach();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        // UNREGISTER SATU RECEIVER UNIVERSAL AGAR TIDAK BOCOR
        if (mPenerimaUniversal != null) { 
            try { requireContext().unregisterReceiver(mPenerimaUniversal); } 
            catch (IllegalArgumentException ignored) {} 
        }
    }

    @Override public void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override public IHumlaObserver getServiceObserver() { return mServiceObserver; }
    @Override public void onServiceBound(IHumlaService service) {
        try { if (mChannelListAdapter == null) setupChannelList(); else mChannelListAdapter.setService(service); } 
        catch (RemoteException e) { e.printStackTrace(); }
    }

    @Override public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);
        if(getService() != null && getService().isConnected()) {
            IHumlaSession session = getService().HumlaSession();
            int foregroundColor = getActivity().getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimaryInverse}).getColor(0, -1);
            IUser self = session.getSessionUser();
            if (self != null) {
                muteItem.setIcon(self.isSelfMuted() ? R.drawable.ic_action_microphone_muted : R.drawable.ic_action_microphone);
                deafenItem.setIcon(self.isSelfDeafened() ? R.drawable.ic_action_audio_muted : R.drawable.ic_action_audio);
                if (muteItem.getIcon() != null) muteItem.getIcon().mutate().setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
                if (deafenItem.getIcon() != null) deafenItem.getIcon().mutate().setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
            }
            menu.findItem(R.id.menu_bluetooth).setChecked(session.usingBluetoothSco());
        }
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu);
        SearchManager sm = (SearchManager) requireActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView sv = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.menu_search));
        sv.setSearchableInfo(sm.getSearchableInfo(requireActivity().getComponentName()));
        sv.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override public boolean onSuggestionSelect(int pos) { return false; }
            @Override public boolean onSuggestionClick(int pos) {
                if (getService() == null || !getService().isConnected()) return false;
                CursorWrapper c = (CursorWrapper) sv.getSuggestionsAdapter().getItem(pos);
                String tipe = c.getString(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA));
                int id = c.getInt(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA));
                try {
                    IHumlaSession s = getService().HumlaSession();
                    if ("channel".equals(tipe)) { if (s.getSessionChannel().getId() != id) s.joinChannel(id); else scrollToChannel(id); } 
                    else if ("user".equals(tipe)) scrollToUser(id);
                } catch (Exception e) { return false; }
                return true;
            }
        });
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (getService() == null || !getService().isConnected()) return super.onOptionsItemSelected(item);
        IHumlaSession s = getService().HumlaSession();
        int id = item.getItemId();
        if (id == R.id.menu_mute_button) {
            try { IUser me = s.getSessionUser(); if (me != null) { boolean m = !me.isSelfMuted(); s.setSelfMuteDeafState(m, m && me.isSelfDeafened()); } } catch (Exception e) {}
            requireActivity().supportInvalidateOptionsMenu(); return true;
        } else if (id == R.id.menu_deafen_button) {
            try { IUser me = s.getSessionUser(); if (me != null) s.setSelfMuteDeafState(me.isSelfDeafened(), !me.isSelfDeafened()); } catch (Exception e) {}
            requireActivity().supportInvalidateOptionsMenu(); return true;
        } else if (id == R.id.menu_bluetooth) {
            item.setChecked(!item.isChecked()); if (item.isChecked()) s.enableBluetoothSco(); else s.disableBluetoothSco(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupChannelList() throws RemoteException {
        mChannelListAdapter = new ChannelListAdapter(requireActivity(), getService(), 
                mDatabaseProvider.getDatabase(), getChildFragmentManager(), 
                isShowingPinnedChannels(), mSettings.shouldShowUserCount());
        
        // ✅ PERBAIKAN KRUSIAL: GANTI attachRecyclerView MENJADI setRecyclerView
        mChannelListAdapter.setRecyclerView(mChannelView);
        
        mChannelListAdapter.setOnChannelClickListener(this);
        mChannelListAdapter.setOnUserClickListener(this);
        mChannelView.setAdapter(mChannelListAdapter);
        mChannelListAdapter.notifyDataSetChanged();
    }

    public void scrollToChannel(int cid) { int p = mChannelListAdapter.getChannelPosition(cid); mChannelView.scrollToPosition(p); }
    public void scrollToUser(int uid) { int p = mChannelListAdapter.getUserPosition(uid); mChannelView.scrollToPosition(p); }
    private boolean isShowingPinnedChannels() { Bundle a = getArguments(); return a != null && a.getBoolean("pinned"); }

    @Override public void onChannelClick(IChannel ch) {
        ChatTargetProvider.ChatTarget t = mTargetProvider.getChatTarget();
        if (t != null && ch.equals(t.getChannel()) && mActionMode != null) mActionMode.finish();
        else mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(ch)) { @Override public void onDestroyActionMode(ActionMode am) { super.onDestroyActionMode(am); mActionMode = null; } });
    }

    @Override public void onUserClick(IUser u) {
        ChatTargetProvider.ChatTarget t = mTargetProvider.getChatTarget();
        if (t != null && u.equals(t.getUser()) && mActionMode != null) mActionMode.finish();
        else mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(u)) { @Override public void onDestroyActionMode(ActionMode am) { super.onDestroyActionMode(am); mActionMode = null; } });
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Settings.PREF_SHOW_USER_COUNT.equals(key) && mChannelListAdapter != null) mChannelListAdapter.setShowChannelUserCount(mSettings.shouldShowUserCount());
    }
}