package com.example.quickstart;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.CalendarScopes;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.util.*;
import java.util.Calendar;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import io.reactivex.android.schedulers.AndroidSchedulers;

import static android.content.ContentValues.TAG;
import static com.example.quickstart.GoogleServicesHelper.acquireGooglePlayServices;
import static com.example.quickstart.GoogleServicesHelper.isGooglePlayServicesAvailable;

public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {

    @BindView(R.id.main_text)
    TextView mOutputText;
    @BindView(R.id.event_list_recycler_view)
    RecyclerView listRecView;

    private CalendarAdapter adapter;
    private List<CalendarEvent> eventsList = new ArrayList<>();

    GoogleAccountCredential mCredential;
    ProgressDialog mProgress;

    private final CompositeDisposable disposables = new CompositeDisposable();

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR_READONLY};

    private com.google.api.services.calendar.Calendar calendarService = null;

    HttpTransport transport = AndroidHttp.newCompatibleTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        adapter = new CalendarAdapter(eventsList);
        listRecView.setLayoutManager(new LinearLayoutManager(this));
        listRecView.setAdapter(adapter);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        getResultsFromApi();
    }

    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!AppUtility.isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            getListFromObservable();
        }
    }

    private void getListFromObservable() {
        disposables.add(calendarObservable()
                // Run on a background thread
                .subscribeOn(Schedulers.io())
                // Be notified on the main thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<List<CalendarEvent>>() {
                    @Override public void onComplete() {
                        adapter.notifyDataSetChanged();
                    }

                    @Override public void onError(Throwable e) {
                        Log.e(TAG, "onError()", e);
                    }

                    @Override public void onNext(List<CalendarEvent> events) {
                        for (CalendarEvent event : events) {
                            eventsList.add(event);
                        }
                    }
                }));
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }


    private List<CalendarEvent> getDataFromApi() throws IOException {
        // List the events of next month from the primary calendar.
        calendarService = new com.google.api.services.calendar.Calendar.Builder(
                transport, jsonFactory, mCredential)
                .setApplicationName("Google Calendar API Android Quickstart")
                .build();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);
        DateTime oneMonthLater = new DateTime(calendar.getTime());
        DateTime now = new DateTime(System.currentTimeMillis());

        List<CalendarEvent> calendarEvents = new ArrayList<CalendarEvent>();
        Events events = calendarService.events().list("primary")
                .setTimeMin(now)
                .setTimeMax(oneMonthLater)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        List<Event> items = events.getItems();

        for (Event event : items) {
            DateTime start = event.getStart().getDateTime();
            if (start == null) {
                // All-day events don't have start times, so just use
                // the start date.
                start = event.getStart().getDate();
            }
            if (event.getStart().getDateTime().toString().contains("T"))
                calendarEvents.add(new CalendarEvent(event.getSummary(), event.getDescription(), event.getStart().getDateTime().toString().substring(0, event.getStart().getDateTime().toString().indexOf("T")) + "", ""));
            else
                calendarEvents.add(new CalendarEvent(event.getSummary(), event.getDescription(), event.getStart().getDateTime().toString() + "", event.getKind()));
        }
        return calendarEvents;
    }

     private Observable<List<CalendarEvent>> calendarObservable() {
        return Observable.defer(new Callable<ObservableSource<? extends List<CalendarEvent>>>() {
            @Override public ObservableSource<? extends List<CalendarEvent>> call() throws Exception {
                // Do some long running operation
                return Observable.just(getDataFromApi());
            }
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}
