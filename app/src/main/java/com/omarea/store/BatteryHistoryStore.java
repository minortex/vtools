package com.omarea.store;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.BatteryManager;

import com.omarea.model.BatteryAvgStatus;
import com.omarea.model.BatteryCycleStats;
import com.omarea.model.BatteryStatus;
import com.omarea.model.PowerHistory;

import java.util.ArrayList;

public class BatteryHistoryStore extends SQLiteOpenHelper {
    public BatteryHistoryStore(Context context) {
        super(context, "battery-history3", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(
                "create table battery_io(" +
                    "time text primary key, " +
                    "temperature REAL default(-1), " +
                    "status int default(-1)," +
                    "mode text," +
                    "io int default(-1)," +
                    "voltage REAL default(0)," +
                    "package text," +
                    "screen_on INTEGER," +
                    "capacity INTEGER" +
                ")");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("alter table battery_io add column voltage REAL default(0)");
            } catch (Exception ignored) {
            }
        }
    }

    public boolean insertHistory(BatteryStatus batteryStatus) {
        SQLiteDatabase database = getWritableDatabase();
        getWritableDatabase().beginTransaction();
        try {
            database.execSQL(
                "insert into battery_io(time, temperature, status, mode, io, voltage, package, screen_on, capacity) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?)", new Object[]{
                    "" + batteryStatus.time,
                    batteryStatus.temperature,
                    batteryStatus.status,
                    batteryStatus.mode,
                    batteryStatus.io,
                    batteryStatus.voltage,
                    batteryStatus.packageName,
                    batteryStatus.screenOn ? 1 : 0,
                    batteryStatus.capacity
            });
            database.setTransactionSuccessful();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            database.endTransaction();
        }
    }

    public int getMaxTemperature() {
        SQLiteDatabase database = getWritableDatabase();
        getWritableDatabase().beginTransaction();
        try {
            Cursor cursor = database.rawQuery("select max(temperature) AS io from battery_io", new String[]{});
            ArrayList<BatteryAvgStatus> data = new ArrayList<>();
            int temperature = 0;
            while (cursor.moveToNext()) {
                temperature = cursor.getInt(0);
            }
            cursor.close();
            return temperature;
        } catch (Exception ignored) {
        } finally {
            database.endTransaction();
        }
        return 0;
    }

    public int lastCapacity() {
        try {
            SQLiteDatabase sqLiteDatabase = this.getReadableDatabase();
            final Cursor cursor = sqLiteDatabase.rawQuery("select TOP(1) capacity from battery_io order by time desc", new String[]{});
            try {
                if (cursor.moveToNext()) {
                    return cursor.getInt(0);
                }
            } finally {
                cursor.close();
                sqLiteDatabase.close();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public int getMaxIO(int batteryStatus) {
        SQLiteDatabase database = getWritableDatabase();
        getWritableDatabase().beginTransaction();
        try {
            Cursor cursor = database.rawQuery("select max(io) AS io from battery_io where status = ? ", new String[]{
                    "" + batteryStatus
            });
            int io = 0;
            while (cursor.moveToNext()) {
                io = cursor.getInt(0);
            }
            cursor.close();
            return io;
        } catch (Exception ignored) {
        } finally {
            database.endTransaction();
        }
        return 0;
    }

    public int getMinIO(int batteryStatus) {
        SQLiteDatabase database = getWritableDatabase();
        getWritableDatabase().beginTransaction();
        try {
            Cursor cursor = database.rawQuery("select min(io) AS io from battery_io where status = ? ", new String[]{
                    "" + batteryStatus
            });
            int io = 0;
            while (cursor.moveToNext()) {
                io = cursor.getInt(0);
            }
            cursor.close();
            return io;
        } catch (Exception ignored) {
        } finally {
            database.endTransaction();
        }
        return 0;
    }

    public ArrayList<BatteryAvgStatus> getAvgData() {
        try {
            SQLiteDatabase sqLiteDatabase = getReadableDatabase();
            Cursor cursor = sqLiteDatabase.rawQuery(
                "select * from (select avg(io) AS io, avg(temperature) as avg, min(temperature) as min, max(temperature) as max, package, mode, count(io), avg(voltage) as voltage from battery_io where status in (?, ?) and package != ? and screen_on = 1 group by package, mode) r order by io",
                new String[]{
                    "" + BatteryManager.BATTERY_STATUS_DISCHARGING,
                    "" + BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                    ""
                });
            ArrayList<BatteryAvgStatus> data = new ArrayList<>();
            while (cursor.moveToNext()) {
                BatteryAvgStatus batteryAvgStatus = new BatteryAvgStatus();
                batteryAvgStatus.io = cursor.getInt(0);
                batteryAvgStatus.avgTemperature = cursor.getInt(1);
                batteryAvgStatus.minTemperature = cursor.getInt(2);
                batteryAvgStatus.maxTemperature = cursor.getInt(3);
                batteryAvgStatus.packageName = cursor.getString(4);
                batteryAvgStatus.mode = cursor.getString(5);
                batteryAvgStatus.count = cursor.getInt(6);
                batteryAvgStatus.voltage = cursor.getFloat(7);
                data.add(batteryAvgStatus);
            }
            cursor.close();
            return data;
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    public ArrayList<PowerHistory> getCurve() {
        ArrayList<PowerHistory> histories = new ArrayList<>();
        try {
            SQLiteDatabase sqLiteDatabase = this.getReadableDatabase();
            final Cursor cursor = sqLiteDatabase.rawQuery(
                "select time, capacity, screen_on, status from battery_io",
                new String[]{}
            );
            PowerHistory prev = null;
            while (cursor.moveToNext()) {
                PowerHistory row = new PowerHistory() {{
                    startTime = cursor.getLong(0);
                    endTime = startTime;
                    capacity = cursor.getInt(1);
                    screenOn = cursor.getInt(2) == 1;
                    charging = cursor.getInt(3) != BatteryManager.BATTERY_STATUS_DISCHARGING;
                }};
                if (prev == null) {
                    prev = row;
                } else if (!(row.capacity == prev.capacity && row.screenOn == prev.screenOn && row.startTime - prev.endTime < 10000)) {
                    histories.add(prev);
                    prev = row;
                } else {
                    prev.endTime = row.endTime;
                }
            }
            if (prev != null) {
                histories.add(prev);
            }
            cursor.close();
            sqLiteDatabase.close();
        } catch (Exception ignored) {
        }
        return histories;
    }

    public BatteryCycleStats getCycleStats() {
        BatteryCycleStats stats = new BatteryCycleStats();
        try {
            SQLiteDatabase sqLiteDatabase = this.getReadableDatabase();
            final Cursor cursor = sqLiteDatabase.rawQuery(
                "select cast(time as integer), capacity, screen_on, status, io, voltage from battery_io order by cast(time as integer)",
                new String[]{}
            );

            Long prevTime = null;
            Integer prevCapacity = null;
            Boolean prevScreenOn = null;
            Integer prevStatus = null;
            long currentSum = 0;
            double voltageSum = 0;
            int dischargeSamples = 0;
            int voltageSamples = 0;
            long screenOnCurrentSum = 0;
            double screenOnVoltageSum = 0;
            int screenOnDischargeSamples = 0;
            int screenOnVoltageSamples = 0;
            int screenOnSamples = 0;

            while (cursor.moveToNext()) {
                long time = cursor.getLong(0);
                int capacity = cursor.getInt(1);
                boolean screenOn = cursor.getInt(2) == 1;
                int status = cursor.getInt(3);
                int io = cursor.getInt(4);
                float voltage = cursor.getFloat(5);

                if (stats.sampleCount == 0) {
                    stats.startTime = time;
                }
                stats.endTime = time;
                stats.sampleCount++;
                if (screenOn) {
                    screenOnSamples++;
                }

                if (isDischarging(status)) {
                    currentSum += Math.abs(io);
                    if (voltage > 0) {
                        voltageSum += voltage;
                        voltageSamples++;
                    }
                    dischargeSamples++;

                    if (screenOn) {
                        screenOnCurrentSum += Math.abs(io);
                        screenOnDischargeSamples++;
                        if (voltage > 0) {
                            screenOnVoltageSum += voltage;
                            screenOnVoltageSamples++;
                        }
                    }
                }

                if (prevTime != null && prevCapacity != null && prevScreenOn != null && prevStatus != null) {
                    long duration = time - prevTime;
                    if (duration > 0) {
                        long measuredDuration = Math.min(duration, 10000);
                        stats.recordedTime += measuredDuration;
                        if (prevScreenOn) {
                            stats.screenOnTime += measuredDuration;
                        } else {
                            stats.screenOffTime += measuredDuration;
                        }

                        if (isDischarging(prevStatus) && isDischarging(status) && prevCapacity > capacity) {
                            int drop = prevCapacity - capacity;
                            stats.capacityDrop += drop;
                            if (prevScreenOn) {
                                stats.screenOnCapacityDrop += drop;
                            } else {
                                stats.screenOffCapacityDrop += drop;
                            }
                        }
                    }
                }

                prevTime = time;
                prevCapacity = capacity;
                prevScreenOn = screenOn;
                prevStatus = status;
            }

            stats.screenOnTime = Math.max(stats.screenOnTime, screenOnSamples * 3000L);

            if (dischargeSamples > 0) {
                stats.avgCurrent = Math.round(currentSum * 1f / dischargeSamples);
                if (voltageSamples > 0) {
                    stats.avgVoltage = (float) (voltageSum / voltageSamples);
                }
            }
            if (screenOnDischargeSamples > 0) {
                stats.screenOnAvgCurrent = Math.round(screenOnCurrentSum * 1f / screenOnDischargeSamples);
                if (screenOnVoltageSamples > 0) {
                    stats.screenOnAvgVoltage = (float) (screenOnVoltageSum / screenOnVoltageSamples);
                }
            }

            cursor.close();
            sqLiteDatabase.close();
        } catch (Exception ignored) {
        }
        return stats;
    }

    private boolean isDischarging(int status) {
        return status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
                status == BatteryManager.BATTERY_STATUS_NOT_CHARGING;
    }

    public boolean clearData() {
        try {
            SQLiteDatabase database = getWritableDatabase();
            database.delete("battery_io", " 1 = 1", new String[]{});
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
