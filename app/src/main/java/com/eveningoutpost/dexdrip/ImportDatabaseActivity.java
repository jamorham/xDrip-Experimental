package com.eveningoutpost.dexdrip;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utils.DatabaseUtil;
import com.eveningoutpost.dexdrip.utils.FileUtils;
import com.eveningoutpost.dexdrip.utils.ListActivityWithMenu;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ImportDatabaseActivity extends ListActivityWithMenu {
    private final static String TAG = ImportDatabaseActivity.class.getSimpleName();
    public static String menu_name = "Import Database";
    AlertDialog progressDialog;
    private Handler mHandler;
    private ArrayList<String> databaseNames;
    private ArrayList<File> databases;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        setContentView(R.layout.activity_import_db);

        if (findAllDatabases()) {
            sortDatabasesAlphabetically();
            showDatabasesInList();
        }
    }

    private void sortDatabasesAlphabetically() {
        Collections.sort(databases, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
    }

    private boolean findAllDatabases() {
        databases = new ArrayList<File>();

        File file = new File(FileUtils.getExternalDir());
        if (!FileUtils.makeSureDirectoryExists(file.getAbsolutePath())) {
            Toast.makeText(this, "Directory does not exist", Toast.LENGTH_LONG).show();
            return false;
        }

        // add from "root"
        addAllDatabases(file, databases);

        // add from level below (Andriod usually unzips to a subdirectory)
        File[] subdirectories = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File path) {
                return path.isDirectory();
            }
        });
        for (int i = 0; i < subdirectories.length; i++) {
            addAllDatabases(subdirectories[i], databases);
        }
        return true;
    }

    private void showDatabasesInList() {
        databaseNames = new ArrayList<String>();

        //show found databases in List
        for (File db : databases) {
            databaseNames.add(db.getName());
        }

        final ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, databaseNames);
        setListAdapter(adapter);

        if (databaseNames.size() == 0) {
            Toast.makeText(this, "No databases found.", Toast.LENGTH_LONG).show();
        }
    }

    private void addAllDatabases(File file, ArrayList<File> databases) {
        File[] files = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.getPath().endsWith(".sqlite")) return true;
                return false;
            }
        });

        for (int i = 0; i < files.length; i++) {
            databases.add(files[i]);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, final int position, long id) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                importDB(position);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //do nothing
            }
        });
        builder.setTitle("Confirm Import");
        builder.setMessage("Do you really want to import '" + databases.get(position).getName() + "'?\n This may negatively affect the data integrity and stability of your system!");
        AlertDialog dialog = builder.create();
        dialog.show();


    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public String getMenuName() {
        return menu_name;
    }

    public int getDBVersion() {

        ApplicationInfo ai = null;
        int version = -1;
        try {
            ai = getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            version = bundle.getInt("AA_DB_VERSION");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } finally {
            return version;
        }
    }


    public void importDB(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Importing, please wait");
        builder.setMessage("Importing, please wait");
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setMessage("Step 1: exporting current DB");
        dialog.setCancelable(false);
        LoadTask lt = new LoadTask(dialog, databases.get(position));
        lt.execute();
    }

    protected void postImportDB(String result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                returnToHome();
            }
        });
        builder.setTitle("Import Result");
        builder.setMessage(result);
        AlertDialog dialog = builder.create();
        dialog.show();

    }

    public void returnToHome() {
        Intent intent = new Intent(this, Home.class);
        CollectionServiceStarter.restartCollectionService(getApplicationContext());
        startActivity(intent);
        finish();
    }

    private class LoadTask extends AsyncTask<Void, Void, String> {

        private final AlertDialog statusDialog;
        private final File dbFile;

        LoadTask(AlertDialog statusDialog, File dbFile) {
            super();
            this.statusDialog = statusDialog;
            this.dbFile = dbFile;
        }


        protected String doInBackground(Void... args) {

            String export = DatabaseUtil.saveSql(getBaseContext());


            if (export == null) {
                statusDialog.dismiss();
                return "Exporting database not successfull... aborting.";
            }


            //Check if db has the correct version:
            SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            int version = db.getVersion();
            db.close();
            if (getDBVersion() != version) {
                statusDialog.dismiss();
                return "Wrong Database version.\n(" + version + " instead of " + getDBVersion() + ")";
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusDialog.setMessage("Step 2: importing DB");
                }
            });

            String result = DatabaseUtil.loadSql(getBaseContext(), dbFile.getAbsolutePath());

            statusDialog.dismiss();
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            postImportDB(result);

        }
    }

}