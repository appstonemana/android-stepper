package id.kotlin.stepper.sample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.horizontal -> {
                horizontal_stepper.visibility = View.VISIBLE
                vertical_stepper.visibility = View.GONE
            }
            R.id.vertical -> {
                vertical_stepper.visibility = View.VISIBLE
                horizontal_stepper.visibility = View.GONE
            }
        }
        return super.onOptionsItemSelected(item)
    }
}