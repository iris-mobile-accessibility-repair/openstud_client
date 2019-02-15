package com.lithium.leona.openstud.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lithium.leona.openstud.R;
import com.lithium.leona.openstud.adapters.ClassroomAdapter;
import com.lithium.leona.openstud.data.InfoManager;
import com.lithium.leona.openstud.data.PreferenceManager;
import com.lithium.leona.openstud.helpers.ClientHelper;
import com.lithium.leona.openstud.helpers.LayoutHelper;
import com.lithium.leona.openstud.helpers.ThemeEngine;
import com.lithium.leona.openstud.listeners.ClickListener;
import com.mancj.materialsearchbar.MaterialSearchBar;
import com.mikepenz.materialdrawer.Drawer;

import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import lithium.openstud.driver.core.models.Classroom;
import lithium.openstud.driver.core.models.Lesson;
import lithium.openstud.driver.exceptions.OpenstudConnectionException;
import lithium.openstud.driver.exceptions.OpenstudInvalidResponseException;

public class SearchClassroomActivity extends BaseDataActivity implements MaterialSearchBar.OnSearchActionListener, MaterialSearchBar.OnClickListener {


    @BindView(R.id.searchBar)
    MaterialSearchBar searchBar;
    @BindView(R.id.main_layout)
    CoordinatorLayout mainLayout;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.recyclerView)
    RecyclerView rv;
    @BindView(R.id.empty_button_reload)
    Button emptyButton;
    @BindView(R.id.empty_text)
    TextView emptyText;
    @BindView(R.id.empty_layout)
    LinearLayout emptyLayout;
    @BindView(R.id.frame)
    FrameLayout contentFrame;
    private Drawer drawer;
    private List<Classroom> classes = new LinkedList<>();
    private ClassroomAdapter adapter;
    private SearchClassroomHandler h = new SearchClassroomHandler(this);
    private boolean touchBarDisabled;

    @OnClick(R.id.empty_button_reload)
    void onClickReloadButton() {
        searchClassrooms(searchBar.getText());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initData()) return;
        ThemeEngine.applySearchClassroomTheme(this);
        setContentView(R.layout.activity_search_classroom);
        ButterKnife.bind(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        searchBar.setPlaceHolder(getResources().getString(R.string.search_classroom));
        //restore last queries from disk
        List oldSuggestions = PreferenceManager.getSuggestions(this);
        if (oldSuggestions != null) searchBar.setLastSuggestions(oldSuggestions);
        emptyText.setText(getResources().getString(R.string.no_classrooms_found));
        drawer = LayoutHelper.applyDrawer(this, toolbar, student);
        rv.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        rv.setLayoutManager(llm);
        adapter = new ClassroomAdapter(mainLayout, this, classes, room -> {
            Bundle bundle = new Bundle();
            Gson gson = new Gson();
            Type listType = new TypeToken<List<Lesson>>() {
            }.getType();
            String json = gson.toJson(room.getTodayLessons(), listType);
            bundle.putSerializable("todayLessons", json);
            bundle.putString("roomName", room.getName());
            bundle.putInt("roomId", room.getInternalId());
            Intent i = new Intent(SearchClassroomActivity.this, ClassroomTimetableActivity.class);
            i.putExtras(bundle);
            startActivity(i);
        });

        rv.setAdapter(adapter);
        setLoadingEnabled(false, false);
        adapter.notifyDataSetChanged();
        setupContentListeners();
        if (PreferenceManager.getClassroomNotificationEnabled(this)) {
            LayoutHelper.createSearchClassroomNotification(this, ThemeEngine.getAlertDialogTheme(this));
            PreferenceManager.setClassroomNotificationEnabled(this, false);
        }
        searchBar.requestFocus();
        searchBar.enableSearch();
    }

    public synchronized void searchClassrooms(String text) {
        new Thread(() -> {
            setLoadingEnabled(true, false);
            List<Classroom> update = null;
            String query = text;
            if (query.endsWith(".")) query = StringUtils.chomp(query);
            try {
                update = os.getClassRoom(query, true);
            } catch (OpenstudInvalidResponseException e) {
                e.printStackTrace();
                if (e.isRateLimit()) h.sendEmptyMessage(ClientHelper.Status.RATE_LIMIT.getValue());
                else h.sendEmptyMessage(ClientHelper.Status.INVALID_RESPONSE.getValue());
            } catch (OpenstudConnectionException e) {
                e.printStackTrace();
                h.sendEmptyMessage(ClientHelper.Status.CONNECTION_ERROR.getValue());
            }
            h.sendEmptyMessage(ClientHelper.Status.OK.getValue());
            updateView(update);
        }).start();
    }

    private void updateView(List<Classroom> update) {
        if (update == null || update.isEmpty()) {
            setLoadingEnabled(false, true);
        } else if (update.equals(classes)) {
            setLoadingEnabled(false, false);
        } else {
            classes.clear();
            classes.addAll(update);
            runOnUiThread(() -> adapter.notifyDataSetChanged());
            setLoadingEnabled(false, false);
        }
    }

    private void setLoadingEnabled(boolean loading, boolean isEmpty) {
        if (loading) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.VISIBLE);
                rv.setVisibility(View.GONE);
                emptyLayout.setVisibility(View.GONE);
            });
        } else {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (!isEmpty) {
                    rv.setVisibility(View.VISIBLE);
                    emptyLayout.setVisibility(View.GONE);
                } else {
                    rv.setVisibility(View.GONE);
                    emptyLayout.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupContentListeners() {
        searchBar.setOnSearchActionListener(this);
        searchBar.setOnClickListener(this);
        GestureDetector gd = new GestureDetector(SearchClassroomActivity.this, new ClickListener());
        View.OnTouchListener otl = (v, event) -> handleTouchEvent(v, event, gd);
        emptyLayout.setOnTouchListener(otl);
        progressBar.setOnTouchListener(otl);
        contentFrame.setOnTouchListener(otl);
        rv.setOnTouchListener(otl);
        searchBar.getSearchEditText().setOnClickListener(this);
        emptyText.setOnTouchListener(otl);
    }

    @Override
    public void onSearchStateChanged(boolean enabled) {
    }

    @Override
    public void onSearchConfirmed(CharSequence text) {
        ClientHelper.hideKeyboard(mainLayout, this);
        if (!text.toString().trim().isEmpty()) searchClassrooms(text.toString().trim());
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen()) drawer.closeDrawer();
        else super.onBackPressed();

    }

    @Override
    public synchronized void onClick(View v) {
        if (searchBar.isSearchEnabled() && !searchBar.isSuggestionsVisible()) {
            searchBar.showSuggestionsList();
        } else if (!searchBar.isSearchEnabled()) searchBar.enableSearch();
    }

    @Override
    public void onButtonClicked(int buttonCode) {
        switch (buttonCode) {
            case MaterialSearchBar.BUTTON_NAVIGATION:
                drawer.openDrawer();
                break;
            case MaterialSearchBar.BUTTON_BACK:
                searchBar.disableSearch();
                break;
            case MaterialSearchBar.BUTTON_DELETE_SUGGESTION:
                PreferenceManager.saveSuggestions(this, searchBar.getLastSuggestions());
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        List oldSuggestions = PreferenceManager.getSuggestions(this);
        if (oldSuggestions != null) {
            searchBar.clearSuggestions();
            searchBar.setLastSuggestions(oldSuggestions);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //save last queries to disk
        PreferenceManager.saveSuggestions(this, searchBar.getLastSuggestions());
    }


    private synchronized boolean handleTouchEvent(View view, MotionEvent event, GestureDetector gd) {
        ClientHelper.hideKeyboard(view, this);
        if (touchBarDisabled) return gd.onTouchEvent(event);
        touchBarDisabled = true;
        if (searchBar.isSearchEnabled() && searchBar.getText().trim().isEmpty())
            searchBar.disableSearch();
        else if (searchBar.isSearchEnabled()) searchBar.hideSuggestionsList();
        new Handler().postDelayed(() -> touchBarDisabled = false, 500);
        return gd.onTouchEvent(event);
    }

    private static class SearchClassroomHandler extends Handler {
        private final WeakReference<SearchClassroomActivity> activity;

        private SearchClassroomHandler(SearchClassroomActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final SearchClassroomActivity activity = this.activity.get();
            if (activity == null) return;
            View.OnClickListener ocl = v -> activity.searchClassrooms(activity.searchBar.getText());
            if (msg.what == ClientHelper.Status.CONNECTION_ERROR.getValue()) {
                LayoutHelper.createActionSnackBar(activity.mainLayout, R.string.connection_error, R.string.retry, Snackbar.LENGTH_LONG, ocl);
            } else if (msg.what == ClientHelper.Status.INVALID_RESPONSE.getValue()) {
                LayoutHelper.createActionSnackBar(activity.mainLayout, R.string.connection_error, R.string.retry, Snackbar.LENGTH_LONG, ocl);
            } else if (msg.what == ClientHelper.Status.RATE_LIMIT.getValue()) {
                LayoutHelper.createActionSnackBar(activity.mainLayout, R.string.rate_limit, R.string.retry, Snackbar.LENGTH_LONG, ocl);
            } else if (msg.what == ClientHelper.Status.USER_NOT_ENABLED.getValue()) {
                LayoutHelper.createTextSnackBar(activity.mainLayout, R.string.user_not_enabled_error, Snackbar.LENGTH_LONG);
            } else if (msg.what == (ClientHelper.Status.INVALID_CREDENTIALS).getValue() || msg.what == ClientHelper.Status.EXPIRED_CREDENTIALS.getValue()) {
                InfoManager.clearSharedPreferences(activity.getApplication());
                Intent i = new Intent(activity, LauncherActivity.class);
                i.putExtra("error", msg.what);
                activity.startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                activity.finish();
            } else if (msg.what == ClientHelper.Status.UNEXPECTED_VALUE.getValue()) {
                LayoutHelper.createTextSnackBar(activity.mainLayout, R.string.invalid_response_error, Snackbar.LENGTH_LONG);
            }
        }
    }

}
