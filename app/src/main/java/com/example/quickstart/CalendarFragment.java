package com.example.quickstart;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

import static android.content.ContentValues.TAG;

/**
 * Created by Zsuzska on 2017. 10. 12..
 */

public class CalendarFragment extends Fragment {
    @BindView(R.id.event_list_recycler_view)
    RecyclerView listRecView;

    private CalendarAdapter adapter;
    private List<CalendarEvent> eventsList = new ArrayList<>();
    private GoogleAccountCredential mCredential;
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyApp.getAppContext());
    private static final String PREF_ACCOUNT_NAME = "accountName";

    private static final String[] SCOPES = {CalendarScopes.CALENDAR_READONLY};

    private com.google.api.services.calendar.Calendar calendarService = null;

    HttpTransport transport = AndroidHttp.newCompatibleTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCredential = ((MainActivity)getActivity()).getmCredential();

        getListFromObservable();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);
        ButterKnife.bind(this, view);

        adapter = new CalendarAdapter(eventsList);
        listRecView.setLayoutManager(new LinearLayoutManager(MyApp.getAppContext()));
        listRecView.setAdapter(adapter);

        return view;
    }

    private void getListFromObservable() {
        disposables.add(calendarObservable()
                // Run on a background thread
                .subscribeOn(Schedulers.io())
                // Be notified on the main thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<List<CalendarEvent>>() {
                    @Override
                    public void onComplete() {
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError()", e);
                    }

                    @Override
                    public void onNext(List<CalendarEvent> events) {
                        for (CalendarEvent event : events) {
                            eventsList.add(event);
                        }
                    }
                }));
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
            @Override
            public ObservableSource<? extends List<CalendarEvent>> call() throws Exception {
                // Do some long running operation
                return Observable.just(getDataFromApi());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
