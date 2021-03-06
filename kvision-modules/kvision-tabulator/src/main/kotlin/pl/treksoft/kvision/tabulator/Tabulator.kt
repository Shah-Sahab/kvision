/*
 * Copyright (c) 2017-present Robert Jaros
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package pl.treksoft.kvision.tabulator

import com.github.snabbdom.VNode
import com.lightningkite.kotlin.observable.list.ObservableList
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.list
import kotlinx.serialization.serializer
import org.w3c.dom.HTMLElement
import pl.treksoft.kvision.KVManagerTabulator
import pl.treksoft.kvision.core.Container
import pl.treksoft.kvision.core.StringBoolPair
import pl.treksoft.kvision.core.Widget
import pl.treksoft.kvision.i18n.I18n
import pl.treksoft.kvision.redux.ReduxStore
import pl.treksoft.kvision.table.TableType
import pl.treksoft.kvision.utils.JSON
import pl.treksoft.kvision.utils.createInstance
import pl.treksoft.kvision.utils.obj
import pl.treksoft.kvision.utils.syncWithList
import redux.RAction
import pl.treksoft.kvision.tabulator.js.Tabulator as JsTabulator

/**
 * Tabulator component.
 *
 * @constructor
 * @param T serializable type
 * @param data a list of serializable objects
 * @param dataUpdateOnEdit determines if the data model is automatically updated after tabulator edit action
 * @param options tabulator options
 * @param types a set of table types
 * @param classes a set of CSS class names
 * @param dataSerializer a serializer for class T
 */
@Suppress("LargeClass", "TooManyFunctions")
open class Tabulator<T : Any>(
    protected val data: List<T>? = null,
    protected val dataUpdateOnEdit: Boolean = true,
    val options: TabulatorOptions<T> = TabulatorOptions(),
    types: Set<TableType> = setOf(),
    classes: Set<String> = setOf(),
    protected val dataSerializer: KSerializer<T>? = null
) :
    Widget(classes) {

    /**
     * Table types.
     */
    var types by refreshOnUpdate(types)

    protected var jsTabulator: JsTabulator? = null

    private var pageSize: Number? = null
    private var currentPage: Number? = null

    protected var filter: ((T) -> Boolean)? = null

    init {
        this.vnkey = "kv_tabulator_$counter"
        if (data != null && dataSerializer != null) {
            @Suppress("UnsafeCastFromDynamic")
            options.data = dataToNative(data, dataSerializer)
            if (data is ObservableList) {
                data.onUpdate += {
                    replaceData(data)
                }
            }
        }
        if (options.langs == null) {
            options.langs = obj {
                default = obj {
                    groups = obj {
                        item = ""
                        items = ""
                    }
                    ajax = obj {
                        loading = "..."
                        error = "!!!"
                    }
                    pagination = obj {
                        page_size = "↕"
                        first = "<<"
                        first_title = "<<"
                        last = ">>"
                        last_title = ">>"
                        prev = "<"
                        prev_title = "<"
                        next = ">"
                        next_title = ">"
                    }
                    headerFilters = obj {
                        default = "..."
                    }
                }
            }
        }
        if (options.rowClick == null) {
            options.rowClick = { _, row ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorRowClick", obj { detail = row })
            }
        }
        if (options.rowDblClick == null) {
            options.rowDblClick = { _, row ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorRowDblClick", obj { detail = row })
            }
        }
        if (options.rowSelectionChanged == null) {
            options.rowSelectionChanged = { _, rows ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorRowSelectionChanged", obj { detail = rows })
            }
        }
        if (options.rowSelected == null) {
            options.rowSelected = { row ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorRowSelected", obj { detail = row })
            }
        }
        if (options.rowDeselected == null) {
            options.rowDeselected = { row ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorRowDeselected", obj { detail = row })
            }
        }
        if (options.cellClick == null) {
            options.cellClick = { _, cell ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorCellClick", obj { detail = cell })
            }
        }
        if (options.cellDblClick == null) {
            options.cellDblClick = { _, cell ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorCellDblClick", obj { detail = cell })
            }
        }
        if (options.cellEditing == null) {
            options.cellEditing = { cell ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorCellEditing", obj { detail = cell })
            }
        }
        if (options.cellEdited == null) {
            options.cellEdited = { cell ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorCellEdited", obj { detail = cell })
            }
        }
        if (options.cellEditCancelled == null) {
            options.cellEditCancelled = { cell ->
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorCellEditCancelled", obj { detail = cell })
            }
        }
        if (options.dataLoading == null && dataSerializer != null) {
            options.dataLoading = { data ->
                val d = nativeToData(data, dataSerializer)
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorDataLoading", obj { detail = d })
            }
        }
        if (options.dataLoaded == null && dataSerializer != null) {
            options.dataLoaded = { data ->
                val d = nativeToData(data, dataSerializer)
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorDataLoaded", obj { detail = d })
            }
        }
        if (options.dataEdited == null && dataSerializer != null) {
            options.dataEdited = { data ->
                val d = nativeToData(data, dataSerializer)
                @Suppress("UnsafeCastFromDynamic")
                this.dispatchEvent("tabulatorDataEdited", obj { detail = d })
                if (dataUpdateOnEdit && this.data is MutableList<T>) {
                    this.data.syncWithList(d)
                }
            }
        }
        counter++
    }

    override fun getSnClass(): List<StringBoolPair> {
        val cl = super.getSnClass().toMutableList()
        types.forEach {
            cl.add(it.type to true)
        }
        return cl
    }

    /**
     * Converts a list of objects of type T to native JS array
     */
    protected fun dataToNative(data: List<T>, dataSerializer: KSerializer<T>): dynamic {
        val json = JSON.plain.stringify(dataSerializer.list, data)
        return kotlin.js.JSON.parse(json)
    }

    /**
     * Converts a native JS array to the list of objects of type T
     */
    protected fun nativeToData(data: dynamic, dataSerializer: KSerializer<T>): List<T> {
        @Suppress("UnsafeCastFromDynamic")
        val str = kotlin.js.JSON.stringify(data)
        return JSON.plain.parse(dataSerializer.list, str)
    }

    /**
     * Creates internal JS Tabulator object
     */
    protected fun createJsTabulator() {
        (this.getElement() as? HTMLElement)?.let {
            jsTabulator =
                KVManagerTabulator.getConstructor()
                    .createInstance(it, options.toJs(this::translate, dataSerializer))
            if (currentPage != null) {
                jsTabulator?.setPageSize(pageSize ?: 0)
                jsTabulator?.setPage(currentPage)
            }
        }
    }

    override fun render(): VNode {
        if (lastLanguage != null && lastLanguage != I18n.language) {
            jsTabulator?.destroy()
            createJsTabulator()
        }
        return render("div")
    }

    override fun afterInsert(node: VNode) {
        createJsTabulator()
    }

    override fun afterDestroy() {
        val page = jsTabulator?.getPage()
        if (page != false) {
            pageSize = jsTabulator?.getPageSize()
            currentPage = page as Number
        }
        jsTabulator?.destroy()
        jsTabulator = null
    }

    /**
     * Silently replaces the data in a table.
     * @param data new data
     */
    open fun replaceData(data: List<T>) {
        if (dataSerializer != null) {
            val native = dataToNative(data, dataSerializer)
            @Suppress("UnsafeCastFromDynamic")
            options.data = native
            jsTabulator?.replaceData(native, null, null)
        }
    }

    /**
     * Sets new data in a table.
     * @param data new data
     */
    open fun setData(data: List<T>) {
        if (dataSerializer != null) {
            val native = dataToNative(data, dataSerializer)
            @Suppress("UnsafeCastFromDynamic")
            options.data = native
            jsTabulator?.setData(native, null, null)
        }
    }

    /**
     * Returns the current data in the table.
     * @param active return only visible data
     * @return current data
     */
    open fun getData(active: Boolean): List<T>? {
        return if (jsTabulator != null && dataSerializer != null) {
            val native = jsTabulator?.getData(active)
            nativeToData(native, dataSerializer)
        } else {
            data
        }
    }

    /**
     * Returns the selected data in the table.
     * @return selected data
     */
    open fun getSelectedData(): List<T> {
        return if (jsTabulator != null && dataSerializer != null) {
            val native = jsTabulator?.getSelectedData()
            nativeToData(native, dataSerializer)
        } else {
            listOf()
        }
    }

    /**
     * Clears the data in the table.
     */
    open fun clearData() = jsTabulator?.clearData()

    /**
     * Undo the last user action.
     */
    open fun undo(): Boolean = jsTabulator?.undo() ?: false

    /**
     * Redo the last undone user action.
     */
    open fun redo(): Boolean = jsTabulator?.redo() ?: false

    /**
     * Get the number of history undo actions available.
     */
    @Suppress("UnsafeCastFromDynamic")
    open fun getHistoryUndoSize(): Int = jsTabulator?.getHistoryUndoSize() ?: 0

    /**
     * Get the number of history redo actions available.
     */
    @Suppress("UnsafeCastFromDynamic")
    open fun getHistoryRedoSize(): Int = jsTabulator?.getHistoryRedoSize() ?: 0

    /**
     * Get the number of data rows.
     * @param activeOnly return only the number of visible rows
     * @return the number of data rows
     */
    open fun getDataCount(activeOnly: Boolean = false): Int = jsTabulator?.getDataCount(activeOnly)?.toInt() ?: 0

    /**
     * Get the HTML code of the table.
     * @param activeOnly include only visible rows
     * @return the HTML code of the table
     */
    open fun getHtml(activeOnly: Boolean = false): String? = jsTabulator?.getHtml(activeOnly)

    /**
     * Scroll to the row given by id.
     * @param row id of the row
     * @param position the scrolling position
     * @param ifVisible scroll to already visible row
     */
    open fun scrollToRow(
        row: Int,
        position: RowPosition? = null,
        ifVisible: Boolean? = null
    ) {
        jsTabulator?.scrollToRow(row, position, ifVisible)
    }

    /**
     * Redraw the table (e.g. after a resize).
     * @param force rerender all rows and columns
     */
    open fun redraw(force: Boolean = false) {
        jsTabulator?.redraw(force)
    }

    /**
     * Change the height of the table.
     * @param height new heigth of the table
     */
    open fun setHeight(height: Int) {
        jsTabulator?.setHeight(height)
    }

    /**
     * Clears current sort.
     */
    open fun clearSort() {
        jsTabulator?.clearSort()
    }

    /**
     * Sets the external filter for the data.
     * @param filter a filtering function
     */
    open fun setFilter(filter: (T) -> Boolean) {
        this.filter = filter
        applyFilter()
    }

    /**
     * Applies the current filter.
     */
    open fun applyFilter() {
        if (dataSerializer != null && filter != null) {
            jsTabulator?.setFilter({ data: dynamic, _: dynamic ->
                val str = kotlin.js.JSON.stringify(data)
                filter?.let { it(JSON.plain.parse(dataSerializer, str)) }
            }, null, null)
        }
    }

    /**
     * Clears current filters.
     * @param includeHeaderFilters clear also the header filters
     */
    open fun clearFilter(includeHeaderFilters: Boolean = true) {
        jsTabulator?.clearFilter(includeHeaderFilters)
    }

    /**
     * Clears header filters.
     */
    open fun clearHeaderFilter() {
        jsTabulator?.clearHeaderFilter()
    }

    /**
     * Select the row given by id.
     * @param row row id
     */
    open fun selectRow(row: Int) {
        jsTabulator?.selectRow(row)
    }

    /**
     * Deselect the row given by id.
     * @param row row id
     */
    open fun deselectRow(row: Int) {
        jsTabulator?.deselectRow(row)
    }

    /**
     * Toggle selection status of the row given by id.
     * @param row row id
     */
    open fun toggleSelectRow(row: Int) {
        jsTabulator?.toggleSelectRow(row)
    }

    /**
     * Returns the selected rows.
     */
    open fun getSelectedRows(): List<JsTabulator.RowComponent> {
        return jsTabulator?.getSelectedRows()?.asList() ?: listOf()
    }

    /**
     * Shows given page.
     * @param page page number
     */
    open fun setPage(page: Int) {
        jsTabulator?.setPage(page)
    }

    /**
     * Shows page with a row given by id.
     * @param row row id
     */
    open fun setPageToRow(row: Int) {
        jsTabulator?.setPageToRow(row)
    }

    /**
     * Set the size of a page.
     * @param size page size
     */
    open fun setPageSize(size: Int) {
        jsTabulator?.setPageSize(size)
    }

    /**
     * Returns the size of a page.
     */
    open fun getPageSize(): Int = jsTabulator?.getPageSize()?.toInt() ?: 0

    /**
     * Navigate to the previous page.
     */
    open fun previousPage() {
        jsTabulator?.previousPage()
    }

    /**
     * Navigate to the next page.
     */
    open fun nextPage() {
        jsTabulator?.nextPage()
    }

    /**
     * Returns current page number.
     */
    open fun getPage(): Int = (jsTabulator?.getPage() as? Number)?.toInt() ?: -1

    /**
     * Returns number of pages.
     */
    open fun getPageMax(): Int = (jsTabulator?.getPageMax() as? Number)?.toInt() ?: -1

    /**
     * Navigate to the previous cell.
     */
    open fun navigatePrev() {
        jsTabulator?.navigatePrev()
    }

    /**
     * Navigate to the next cell.
     */
    open fun navigateNext() {
        jsTabulator?.navigateNext()
    }

    /**
     * Navigate to the cell on the left.
     */
    open fun navigateLeft() {
        jsTabulator?.navigateLeft()
    }

    /**
     * Navigate to the cell on the right.
     */
    open fun navigateRight() {
        jsTabulator?.navigateRight()
    }

    /**
     * Navigate to the same cell in the row above.
     */
    open fun navigateUp() {
        jsTabulator?.navigateUp()
    }

    /**
     * Navigate to the same cell in the row below.
     */
    open fun navigateDown() {
        jsTabulator?.navigateDown()
    }

    companion object {
        internal var counter = 0

        /**
         * DSL builder extension function.
         *
         * It takes the same parameters as the constructor of the built component.
         */
        inline fun <reified T : Any> Container.tabulator(
            data: List<T>? = null,
            dataUpdateOnEdit: Boolean = true,
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            noinline init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val tabulator = create(data, dataUpdateOnEdit, options, types, classes)
            init?.invoke(tabulator)
            this.add(tabulator)
            return tabulator
        }

        /**
         * DSL builder extension function for general redux store.
         */
        inline fun <reified T : Any, S : Any, A : RAction> Container.tabulator(
            store: ReduxStore<S, A>,
            noinline dataFactory: (S) -> List<T>,
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            noinline init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val tabulator = create(store, dataFactory, options, types, classes)
            init?.invoke(tabulator)
            this.add(tabulator)
            return tabulator
        }

        /**
         * DSL builder extension function for dedicated redux store (backed with a list).
         */
        inline fun <reified T : Any, A : RAction> Container.tabulator(
            store: ReduxStore<List<T>, A>,
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            noinline init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val tabulator = create(store, options, types, classes)
            init?.invoke(tabulator)
            this.add(tabulator)
            return tabulator
        }

        /**
         * DSL builder extension function for dynamic data (send within options parameter).
         */
        fun <T : Any> Container.tabulator(
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val tabulator = Tabulator(dataUpdateOnEdit = false, options = options, types = types, classes = classes)
            init?.invoke(tabulator)
            this.add(tabulator)
            return tabulator
        }

        /**
         * A helper function to create a Tabulator object with correct serializer.
         */
        @UseExperimental(ImplicitReflectionSerializer::class)
        inline fun <reified T : Any> create(
            data: List<T>? = null,
            dataUpdateOnEdit: Boolean = true,
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            noinline init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val tabulator = Tabulator(data, dataUpdateOnEdit, options, types, classes, T::class.serializer())
            init?.invoke(tabulator)
            return tabulator
        }

        /**
         * A helper function to create a Tabulator object with correct serializer and general redux store.
         */
        @UseExperimental(ImplicitReflectionSerializer::class)
        inline fun <reified T : Any, S : Any, A : RAction> create(
            store: ReduxStore<S, A>,
            noinline dataFactory: (S) -> List<T>,
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            noinline init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val data = dataFactory(store.getState())
            val tabulator = Tabulator(data, false, options, types, classes, T::class.serializer())
            init?.invoke(tabulator)
            store.subscribe { s ->
                tabulator.replaceData(dataFactory(s))
            }
            return tabulator
        }

        /**
         * A helper function to create a Tabulator object with correct serializer and dedicated redux store.
         */
        @UseExperimental(ImplicitReflectionSerializer::class)
        inline fun <reified T : Any, A : RAction> create(
            store: ReduxStore<List<T>, A>,
            options: TabulatorOptions<T> = TabulatorOptions(),
            types: Set<TableType> = setOf(),
            classes: Set<String> = setOf(),
            noinline init: (Tabulator<T>.() -> Unit)? = null
        ): Tabulator<T> {
            val data = store.getState()
            val tabulator = Tabulator(data, false, options, types, classes, T::class.serializer())
            init?.invoke(tabulator)
            store.subscribe { s ->
                tabulator.replaceData(s)
            }
            return tabulator
        }
    }
}
