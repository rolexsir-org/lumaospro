package com.rolexm.launcher.launcher

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import com.rolexm.launcher.R
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.launcher.model.HomeItem

/** Actions available when an app is long-pressed in the drawer. */
interface DrawerItemActions {
    fun addToHome(entry: AppEntry)
    fun addToDock(entry: AppEntry)
    fun appInfo(entry: AppEntry)
    fun uninstall(entry: AppEntry)
}

/** Actions available when a home or dock item is long-pressed. */
interface HomeItemActions {
    fun remove(position: Int)
    fun renameFolder(position: Int)
    fun mergeIntoFolder(position: Int, targetPosition: Int)
    fun removeFromFolder(position: Int, childKey: ComponentKey)
    fun appInfo(key: ComponentKey)
    fun uninstall(key: ComponentKey)
}

/**
 * Long-press menus.
 *
 * Uses the platform PopupMenu rather than a custom popup: it is themed by the
 * system, it handles keyboard and accessibility dismissal, and it costs nothing
 * to maintain.
 *
 * Every destructive entry routes through the system UI (app info, uninstall
 * confirmation). ROLEXM never removes anything on its own.
 */
object AppMenus {

    fun showDrawerMenu(
        context: Context,
        anchor: View,
        entry: AppEntry,
        actions: DrawerItemActions
    ) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            add(0, MENU_ADD_HOME, 0, R.string.action_add_to_home)
            add(0, MENU_ADD_DOCK, 1, R.string.action_add_to_dock)
            add(0, MENU_APP_INFO, 2, R.string.action_app_info)
            add(0, MENU_UNINSTALL, 3, R.string.action_uninstall)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_ADD_HOME -> actions.addToHome(entry)
                MENU_ADD_DOCK -> actions.addToDock(entry)
                MENU_APP_INFO -> actions.appInfo(entry)
                MENU_UNINSTALL -> actions.uninstall(entry)
            }
            true
        }
        popup.show()
    }

    /**
     * @param candidates other positions on the same row that this item could be
     *                   merged into a folder with, as "position -> label" pairs.
     */
    fun showHomeItemMenu(
        context: Context,
        anchor: View,
        item: HomeItem,
        position: Int,
        candidates: List<Pair<Int, String>>,
        actions: HomeItemActions
    ) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            if (item is HomeItem.Folder) {
                add(0, MENU_RENAME, 0, R.string.action_rename_folder)
            }
            if (candidates.isNotEmpty()) {
                val merge = addSubMenu(0, MENU_MERGE, 1, R.string.action_merge_into_folder)
                candidates.forEach { (targetPosition, label) ->
                    merge.add(0, MENU_MERGE_BASE + targetPosition, 0, label)
                }
            }
            add(0, MENU_REMOVE, 90, R.string.action_remove_from_home)
            when (item) {
                is HomeItem.App -> {
                    add(0, MENU_APP_INFO, 100, R.string.action_app_info)
                    add(0, MENU_UNINSTALL, 101, R.string.action_uninstall)
                }
                is HomeItem.Folder -> item.children.forEachIndexed { index, key ->
                    add(0, MENU_CHILD_BASE + index, 110 + index, removeChildLabel(context, key))
                }
            }
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_RENAME -> actions.renameFolder(position)
                MENU_REMOVE -> actions.remove(position)
                MENU_APP_INFO -> (item as? HomeItem.App)?.let { actions.appInfo(it.key) }
                MENU_UNINSTALL -> (item as? HomeItem.App)?.let { actions.uninstall(it.key) }
                else -> {
                    val id = menuItem.itemId
                    if (id >= MENU_MERGE_BASE) {
                        actions.mergeIntoFolder(position, id - MENU_MERGE_BASE)
                    } else if (id >= MENU_CHILD_BASE) {
                        val childIndex = id - MENU_CHILD_BASE
                        (item as? HomeItem.Folder)?.children?.getOrNull(childIndex)
                            ?.let { actions.removeFromFolder(position, it) }
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun removeChildLabel(context: Context, key: ComponentKey): String =
        context.getString(R.string.action_remove_from_folder, key.packageName)

    // IDs are spaced so that "base + index" ranges never overlap.
    private const val MENU_ADD_HOME = 1
    private const val MENU_ADD_DOCK = 2
    private const val MENU_APP_INFO = 3
    private const val MENU_UNINSTALL = 4
    private const val MENU_RENAME = 5
    private const val MENU_MERGE = 6
    private const val MENU_REMOVE = 7

    private const val MENU_MERGE_BASE = 10_000
    private const val MENU_CHILD_BASE = 20_000
}
