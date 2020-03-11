package de.markusfisch.android.binaryeye.app

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.AbsListView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import de.markusfisch.android.binaryeye.R

val systemBarScrollListener = object : AbsListView.OnScrollListener {
	override fun onScroll(
		view: AbsListView,
		firstVisibleItem: Int,
		visibleItemCount: Int,
		totalItemCount: Int
	) {
		// give Android some time to settle down before running this;
		// not putting it on the queue makes it only work sometimes
		view.post {
			val scrolled = firstVisibleItem > 0 ||
					(totalItemCount > 0 && view.getChildAt(0).top < 0)
			val scrollable = if (scrolled) true else totalItemCount > 0 &&
					view.getChildAt(view.lastVisiblePosition).bottom >= view.height
			colorSystemAndToolBars(view.context, scrolled, scrollable)
		}
	}

	override fun onScrollStateChanged(
		view: AbsListView,
		scrollState: Int
	) {
	}
}

fun initSystemBars(activity: AppCompatActivity) {
	activity.window.decorView.systemUiVisibility =
		View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
				View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
	colorSystemAndToolBars(activity)
}

fun colorSystemAndToolBars(
	context: Context,
	scrolled: Boolean = false,
	scrollable: Boolean = false
) {
	val translucentColor = getTranslucentPrimaryColor(context)
	val topColor = if (scrolled) translucentColor else 0
	val bottomColor = if (scrolled || scrollable) translucentColor else 0
	val activity = getAppCompatActivity(context) ?: return
	val window = activity.window
	window.statusBarColor = topColor
	window.navigationBarColor = bottomColor
	activity.supportActionBar?.setBackgroundDrawable(ColorDrawable(topColor))
}

private fun getTranslucentPrimaryColor(context: Context) = ContextCompat.getColor(
	context,
	R.color.primary
) and 0xffffff or 0xd8000000.toInt()

private fun getAppCompatActivity(context: Context): AppCompatActivity? {
	var ctx = context
	while (ctx is ContextWrapper) {
		if (ctx is AppCompatActivity) {
			return ctx
		}
		ctx = ctx.baseContext ?: break
	}
	return null
}

private var windowInsetsListener: ((insets: Rect) -> Unit)? = null
// invoke this from Fragment.onCreateView() *or* Activity.onCreate()
fun setWindowInsetListener(listener: (insets: Rect) -> Unit) {
	windowInsetsListener = listener
}

// invoke this from Activity.onCreate() to setup listening for insets
fun setupInsets(view: View, toolbar: Toolbar) {
	val toolBarHeight = toolbar.layoutParams.height
	ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
		// runs *after* layout and thus *after* Fragment.onCreateView()
		if (insets.hasSystemWindowInsets()) {
			val windowInsets = Rect(
				insets.systemWindowInsetLeft,
				insets.systemWindowInsetTop + toolBarHeight,
				insets.systemWindowInsetRight,
				insets.systemWindowInsetBottom
			)
			windowInsetsListener?.also { it(windowInsets) }
		}
		insets
	}
}
