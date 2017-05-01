/*******************************************************************************
 * Copyright (C) 2015, 2016 RAPID EU Project
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *******************************************************************************/
package eu.project.rapid.demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Random;

import eu.project.rapid.ac.DFE;
import eu.project.rapid.common.Clone;
import eu.project.rapid.common.RapidConstants;
import eu.project.rapid.common.RapidConstants.COMM_TYPE;
import eu.project.rapid.gvirtus.GVirtusDemo;
import eu.project.rapid.queens.NQueens;
import eu.project.rapid.sudoku.Sudoku;
import eu.project.rapid.synthBenchmark.JniTest;

/**
 * The class that handles configuration parameters and starts the offloading process.
 */
public class DemoActivity extends Activity implements DFE.DfeCallback {

    private static final String TAG = "DemoActivity";

    public static int nrClones = 1;
    private TextView textViewVmConnected;
    private Handler handler;

    private DFE dfe;

    private int nQueensLocalNr;
    private double nQueensLocalTotDur;
    private int nQueensRemoteNr;
    private double nQueensRemoteTotDur;
    private TextView nQueensLocalNrText;
    private TextView nQueensLocalDurText;
    private TextView nQueensRemoteNrText;
    private TextView nQueensRemoteDurText;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.i(TAG, "onCreate");

        String vmIp = getIntent().getStringExtra(MainActivity.KEY_VM_IP);
        boolean useRapidInfrastructure = getIntent().getBooleanExtra(MainActivity.KEY_USE_RAPID_INFRASTRUCTURE, false);
        COMM_TYPE commType = (COMM_TYPE) getIntent().getSerializableExtra(MainActivity.KEY_CONN_TYPE);
        boolean usePrevVm = getIntent().getBooleanExtra(MainActivity.KEY_USE_PREV_VM, true);

        handler = new Handler();

        LinearLayout layoutNrClones = (LinearLayout) findViewById(R.id.layoutNrClones);
        if (useRapidInfrastructure) {
            layoutNrClones.setVisibility(View.VISIBLE);
            Spinner nrClonesSpinner = (Spinner) findViewById(R.id.spinnerNrClones);
            nrClonesSpinner.setOnItemSelectedListener(new NrClonesSelectedListener());
        } else {
            layoutNrClones.setVisibility(View.GONE);
        }

        textViewVmConnected = (TextView) findViewById(R.id.textVmConnectionStatus);

        nQueensLocalNrText = (TextView) findViewById(R.id.valNQueensLocalNr);
        nQueensLocalDurText = (TextView) findViewById(R.id.valNQueensLocalTime);
        nQueensRemoteNrText = (TextView) findViewById(R.id.valNQueensRemoteNr);
        nQueensRemoteDurText = (TextView) findViewById(R.id.valNQueensRemoteTime);

        // If we don't specify the IP of the VM, we assume that we are using the Rapid infrastructure,
        // i.e. the DS, the VMM, the SLAM, etc., which means that the DFE will select automatically a
        // VM. We leave the user select a VM manually for fast deploy and testing.
        if (vmIp == null) {
            dfe = DFE.getInstance(getPackageName(), getPackageManager(), this);
        } else {
            dfe = DFE.getInstance(getPackageName(), getPackageManager(), this,
                    new Clone("", vmIp), commType);
        }
    }

    private class VmConnectionStatusUpdater implements Runnable {

        private COMM_TYPE commType;
        private boolean isConnected;

        VmConnectionStatusUpdater(boolean isConnected, COMM_TYPE commType) {
            this.isConnected = isConnected;
            this.commType = commType;
        }

        public void run() {
            handler.post(new Runnable() {
                public void run() {
                    textViewVmConnected.setTextColor(Color.GREEN);
                    if (isConnected) {
                        if (commType.equals(COMM_TYPE.CLEAR)) {
                            textViewVmConnected.setText(R.string.textVmConnectedClear);
                        } else if (commType.equals(COMM_TYPE.SSL)) {
                            textViewVmConnected.setText(R.string.textVmConnectedSSL);
                        }
                    } else {
                        textViewVmConnected.setTextColor(Color.RED);
                        textViewVmConnected.setText(R.string.textVmDisconnected);
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        if (dfe != null) {
            dfe.onDestroy();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "OnPause");
    }

    public void onClickJni1(View v) {
        JniTest jni = new JniTest(dfe);

        String result = jni.jniCaller();
        Log.i(TAG, "Result of jni invocation: " + result);

        Toast.makeText(DemoActivity.this, result, Toast.LENGTH_SHORT).show();
    }

    public void onClickSudoku(View v) {

        Sudoku sudoku = new Sudoku(dfe);

        boolean result = sudoku.hasSolution();

        if (result) {
            Toast.makeText(DemoActivity.this, "Sudoku has solution", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(DemoActivity.this, "Sudoku does not have solution", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void onClickQueenSolver(View v) {
        new NQueensTask().execute();
    }

    private class NQueensTask extends AsyncTask<Void, Void, Integer> {
        int nrQueens;
        Spinner nrQueensSpinner = (Spinner) findViewById(R.id.spinnerNrQueens);
        // Show a spinning dialog while solving the puzzle
        ProgressDialog pd = ProgressDialog.show(DemoActivity.this, "Working...", "Solving N Queens...", true, false);

        public NQueensTask() {
            this.nrQueens = Integer.parseInt((String) nrQueensSpinner.getSelectedItem());
        }

        public NQueensTask(int nrQueens) {
            this.nrQueens = nrQueens;
            // Show a spinning dialog while solving the puzzle
            pd = ProgressDialog.show(DemoActivity.this, "Working...", "Solving N Queens...", true, false);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            NQueens puzzle = new NQueens(dfe, nrClones);
            return puzzle.solveNQueens(nrQueens);
        }

        @Override
        protected void onPostExecute(Integer result) {
            Log.i(TAG, "Finished execution");
            if (pd != null) {
                pd.dismiss();
            }
            Log.i(TAG, nrQueens + "-Queens solved, solutions: " + result);
            if (dfe.getLastExecLocation(getPackageName(), "localSolveNQueens").equals(RapidConstants.ExecLocation.LOCAL)) {
                nQueensLocalNrText.setText(String.format(Locale.ENGLISH, "%d", ++nQueensLocalNr));
                nQueensLocalTotDur += dfe.getLastExecDuration(getPackageName(), "localSolveNQueens");
                nQueensLocalDurText.setText(String.format(Locale.ENGLISH, "%.2f", nQueensLocalTotDur / nQueensLocalNr / 1000000));
            } else {
                nQueensRemoteNrText.setText(String.format(Locale.ENGLISH, "%d", ++nQueensRemoteNr));
                nQueensRemoteTotDur += dfe.getLastExecDuration(getPackageName(), "localSolveNQueens");
                nQueensRemoteDurText.setText(String.format(Locale.ENGLISH, "%.2f", nQueensRemoteTotDur / nQueensRemoteNr / 1000000));
            }
        }
    }

    public void onClickMultipleQueenSolver(View v) {
        new NQueensMultipleTask().execute();
    }

    private class NQueensMultipleTask extends AsyncTask<Void, Void, Void> {
        // Show a spinning dialog while solving the puzzle
        ProgressDialog pd = ProgressDialog.show(DemoActivity.this, "Working...", "Solving multiple N Queens...", true, false);

        @Override
        protected Void doInBackground(Void... params) {
            final Random r = new Random();
            for (int i = 0; i < 10; i++) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        NQueens puzzle = new NQueens(dfe, nrClones);
                        int nrQueens = 4 + r.nextInt(4);
                        Log.v(Thread.currentThread().getName(), "Started " + nrQueens + "-queens");
                        int result = puzzle.solveNQueens(nrQueens);
                        Log.v(Thread.currentThread().getName(), "Finished " + nrQueens + "-queens, " + result + " solutions");
                    }
                }).start();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            Log.i(TAG, "Finished execution");
            if (pd != null) {
                pd.dismiss();
            }
        }
    }

    public void onClickGvirtusDemo(View v) {
        new GvirtusCaller().execute();
    }

    private class GvirtusCaller extends AsyncTask<Void, Void, Void> {
        // Show a spinning dialog while running the GVirtuS demo
        ProgressDialog pd = ProgressDialog.show(DemoActivity.this, "Working...",
                "Running the GVirtuS demo...", true, false);

        @Override
        protected Void doInBackground(Void... params) {
            int nrTests = 1;

            GVirtusDemo gvirtusDemo = new GVirtusDemo(dfe);
            for (int i = 0; i < nrTests; i++) {
                Log.i(TAG, "------------ Started running the GVirtuS deviceQuery demo.");
                try {
                    gvirtusDemo.deviceQuery();
                    Log.i(TAG, "Correctly executed the GVirtuS deviceQuery demo.");
                } catch (IOException e) {
                    Log.e(TAG, "Error while running the GVirtuS deviceQuery demo: " + e);
                }
            }

            for (int i = 0; i < nrTests; i++) {
                Log.i(TAG,
                        "------------ Started running the GVirtuS matrixMul demo. " + Charset.defaultCharset());
                try {
                    gvirtusDemo.matrixMul2();
                    Log.i(TAG, "Correctly executed the GVirtuS matrixMul demo.");
                } catch (IOException e) {
                    Log.e(TAG, "Error while running the GVirtuS matrixMul demo: " + e);
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Log.i(TAG, "Finished execution");
            if (pd != null) {
                pd.dismiss();
            }
        }
    }

    public void onRadioExecLocationChecked(View radioButton) {
        switch (radioButton.getId()) {

            case R.id.radio_local:
                dfe.setUserChoice(RapidConstants.ExecLocation.LOCAL);
                break;

            case R.id.radio_remote:
                dfe.setUserChoice(RapidConstants.ExecLocation.REMOTE);
                break;

            case R.id.radio_exec_time_energy:
                dfe.setUserChoice(RapidConstants.ExecLocation.DYNAMIC);
                break;
        }
    }

    private class NrClonesSelectedListener implements OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

            nrClones = Integer.parseInt((String) parent.getItemAtPosition(pos));
            Log.i(TAG, "Number of clones: " + nrClones);
            dfe.setNrClones(nrClones);
        }

        public void onNothingSelected(AdapterView<?> arg0) {
            Log.i(TAG, "Nothing selected on clones spinner");
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void vmConnectionStatusUpdate(boolean isConnected, COMM_TYPE commType) {
        new Thread(new VmConnectionStatusUpdater(isConnected, commType)).start();
    }
}