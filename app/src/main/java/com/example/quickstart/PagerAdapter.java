package com.example.quickstart;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * Created by Zsuzska on 2017. 10. 15..
 */

public class PagerAdapter extends FragmentPagerAdapter {
    int mNumOfTabs;

    public PagerAdapter(FragmentManager fm, int numOfTabs) {
        super(fm);
        this.mNumOfTabs = numOfTabs;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new CalendarFragment();
            case 1:
                return new CalendarFragment();
            default:
                return null;
        }
    }

        @Override
        public int getCount () {
            return mNumOfTabs;
        }
    }
