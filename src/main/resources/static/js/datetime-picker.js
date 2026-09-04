/*
 * 自製日期／時間選擇器
 * ============================================================
 * 使用者要求「點下去選時間日期的呈現方式」不要用瀏覽器原生的 input[type=date]/input[type=time] 那個
 * 點下去彈出來的日曆/時鐘小視窗——那個彈窗完全是瀏覽器自己畫的, 沒辦法用 CSS 改樣式, 每個瀏覽器/系統
 * 長得也不一樣。這支腳本把頁面上所有 <input type="date"> / <input type="time"> 換成「按鈕觸發、自己畫的
 * 彈出面板」, 跟整個網站的卡片/配色風格一致。
 *
 * 設計原則: 盡量不動到既有的表單/JS 邏輯——
 *   - 原本的 <input type="date"/"time"> 元素還在 DOM 裡、name/id/value/disabled/required 都保留,
 *     只是整個疊在新畫出來的按鈕(.dtp-trigger)下面、視覺上看不見 (opacity:0, 但不是 display:none,
 *     瀏覽器內建的表單驗證跟 :invalid 樣式才不會失效)。表單送出時讀到的還是這個原生 input 的 value,
 *     完全不用改後端或改其他 JS 邏輯。
 *   - 使用者在彈出面板選日期/時間時, 這支腳本會把值寫回原生 input.value, 並 dispatch 'change' 事件
 *     (bubbles:true)——所有原本掛在這些 input 上的 onchange="..." (例如 this.form.requestSubmit()、
 *     updateStartTime() 這些) 完全不用改, 照樣會被觸發。
 *   - 頁面上任何時候用 innerHTML/範本字串動態插入新的 date/time input (例如行程編輯看板拖曳排序後
 *     重新渲染整個清單), 不用另外呼叫初始化——用 MutationObserver 監看整個文件, 有新的 date/time input
 *     出現就自動套用, 完全不用去改動每一處重新渲染的程式碼。
 *
 * 引入方式: 在頁面 <head> 或 <body> 結尾加
 *   <link rel="stylesheet" href="/css/datetime-picker.css">
 *   <script src="/js/datetime-picker.js"></script>
 * 不需要再呼叫任何初始化函式, 這支腳本自己會在 DOMContentLoaded 時掃描一次、之後持續監看新增的元素。
 *
 * 如果某個 date/time input 就是不想被這支腳本接管 (例如刻意要用原生的), 加上屬性 data-dtp-skip 即可。
 */
(function () {
    'use strict';

    var WEEKDAY_LABELS = ['日', '一', '二', '三', '四', '五', '六'];
    var MONTH_LABELS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

    var openPanel = null; // 目前開著的面板 (同一時間只允許開一個)
    var openTrigger = null;

    // 這個網站其他地方 (行程編輯看板尤其多) 常常會直接用 JS 幫 date/time input 設定 `.value = 'xx:xx'`
    // 來同步顯示 (例如「出發時間」欄位要跟著時間表算出來的第一個項目時間更新), 完全不會另外 dispatch
    // change/input 事件——如果只靠監聽 change/input 事件或 MutationObserver 屬性異動來更新按鈕上顯示的
    // 文字, 這種「安靜地」直接改 .value 的地方全部都要重新去改一次才會同步, 範圍太大、風險也高。改成
    // 直接覆寫 HTMLInputElement.prototype.value 的 setter, 只要是這個網站任何地方對一個已經被這支腳本
    // 接管的 date/time input 設定 .value, 都會自動連帶更新按鈕上顯示的文字, 完全不用去改動其他既有程式碼。
    (function patchValueSetter() {
        var proto = window.HTMLInputElement && window.HTMLInputElement.prototype;
        var desc = proto && Object.getOwnPropertyDescriptor(proto, 'value');
        if (!desc || !desc.configurable || !desc.set) return;
        Object.defineProperty(proto, 'value', {
            get: desc.get,
            set: function (v) {
                desc.set.call(this, v);
                if (this.__dtpTrigger) refreshTrigger(this);
            },
            configurable: true,
            enumerable: desc.enumerable
        });
    })();

    function pad2(n) { return String(n).padStart(2, '0'); }

    function parseIsoDate(v) {
        if (!v) return null;
        var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v);
        if (!m) return null;
        var d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
        return isNaN(d.getTime()) ? null : d;
    }

    function formatIsoDate(d) {
        return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
    }

    function formatDateDisplay(d) {
        return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日（' + WEEKDAY_LABELS[d.getDay()] + '）';
    }

    function parseTime(v) {
        if (!v) return null;
        var m = /^(\d{1,2}):(\d{2})/.exec(v);
        if (!m) return null;
        var h = Number(m[1]), min = Number(m[2]);
        if (h > 23 || min > 59) return null;
        return { h: h, m: min };
    }

    function closeOpenPanel() {
        if (openPanel && openPanel.parentNode) openPanel.parentNode.removeChild(openPanel);
        if (openTrigger) openTrigger.classList.remove('dtp-open');
        openPanel = null;
        openTrigger = null;
    }

    document.addEventListener('mousedown', function (e) {
        if (!openPanel) return;
        if (openPanel.contains(e.target) || (openTrigger && openTrigger.contains(e.target))) return;
        closeOpenPanel();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') closeOpenPanel();
    });
    // 使用者回報「時間選擇面板沒辦法用滾輪」——原因是這裡用 capture:true 監聽 window 的 'scroll' 事件,
    // 目的是「頁面本身捲動時, 面板位置沒跟著動, 乾脆直接關掉」。但 scroll 事件雖然不會 bubble, capture
    // 階段還是會從 window 往下經過每一層祖先節點, 所以面板裡「時間欄」(.dtp-time-col, overflow-y:auto)
    // 自己被滑鼠滾輪捲動時, 那個「這個 div 自己捲動了」的 scroll 事件一樣會在 capture 階段被這裡攔到,
    // 於是使用者才剛開始滾, 面板就立刻被 closeOpenPanel() 整個拆掉——看起來就像「滾輪完全沒反應」,
    // 而且如果面板是在滾動中途被拆掉, 使用者下一次點擊也可能點到已經不存在的舊面板殘留位置, 變成
    // 「點下去沒反應」。修法: 只有捲動的對象不是面板自己(或面板裡面的東西)時才關閉——也就是只在「頁面
    // 本身在捲動」時才觸發, 面板內部自己的捲動不算。
    window.addEventListener('scroll', function (e) {
        if (openPanel && e.target && typeof e.target.nodeType === 'number' && openPanel.contains(e.target)) return;
        closeOpenPanel();
    }, true);
    window.addEventListener('resize', function () { closeOpenPanel(); });

    function positionPanel(panel, trigger) {
        var r = trigger.getBoundingClientRect();
        document.body.appendChild(panel); // 先掛上去才量得到 offsetHeight/offsetWidth
        var panelW = panel.offsetWidth, panelH = panel.offsetHeight;
        // 面板通常比觸發按鈕本身寬 (例如行程規劃的「出發時間」欄位在 grid 版面裡只有 120px, 但時間
        // 面板要 200px 才放得下兩欄數字)。如果直接貼齊按鈕左邊, 較寬的面板會整個往右延伸, 蓋住右邊
        // 緊接著的下一個欄位 (例如「抵達機場」)。改成以按鈕「置中」對齊來開面板, 讓多出來的寬度平均
        // 分攤到左右兩側, 比較不會整個蓋住單一邊的鄰居欄位。
        var left = r.left + (r.width - panelW) / 2;
        if (left + panelW > window.innerWidth - 8) left = window.innerWidth - panelW - 8;
        if (left < 8) left = 8;
        var top = r.bottom + 6;
        if (top + panelH > window.innerHeight - 8) top = r.top - panelH - 6; // 下面放不下就往上開
        if (top < 8) top = 8;
        panel.style.left = left + 'px';
        panel.style.top = top + 'px';
    }

    function setInputValue(input, value) {
        input.value = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    // ---------------- 日期面板 ----------------
    function openDatePanel(trigger, input) {
        closeOpenPanel();
        var selected = parseIsoDate(input.value);
        var view = selected ? new Date(selected.getFullYear(), selected.getMonth(), 1) : (function () {
            var t = new Date(); return new Date(t.getFullYear(), t.getMonth(), 1);
        })();
        var mode = 'days'; // 'days' | 'months' | 'years'
        var yearPageStart = view.getFullYear() - (view.getFullYear() % 12);

        var panel = document.createElement('div');
        panel.className = 'dtp-panel dtp-date';

        function render() {
            panel.innerHTML = '';
            var head = document.createElement('div');
            head.className = 'dtp-cal-head';

            var prevBtn = document.createElement('button');
            prevBtn.type = 'button'; prevBtn.className = 'dtp-nav'; prevBtn.innerHTML = '<i class="fa-solid fa-chevron-left"></i>';
            var title = document.createElement('div');
            title.className = 'dtp-title';
            var nextBtn = document.createElement('button');
            nextBtn.type = 'button'; nextBtn.className = 'dtp-nav'; nextBtn.innerHTML = '<i class="fa-solid fa-chevron-right"></i>';

            head.appendChild(prevBtn); head.appendChild(title); head.appendChild(nextBtn);
            panel.appendChild(head);

            var grid = document.createElement('div');
            grid.className = 'dtp-cal-grid';

            if (mode === 'days') {
                title.textContent = view.getFullYear() + '年 ' + MONTH_LABELS[view.getMonth()];
                title.onclick = function () { mode = 'months'; render(); };
                prevBtn.onclick = function () { view.setMonth(view.getMonth() - 1); render(); };
                nextBtn.onclick = function () { view.setMonth(view.getMonth() + 1); render(); };

                WEEKDAY_LABELS.forEach(function (w) {
                    var el = document.createElement('div'); el.className = 'dtp-dow'; el.textContent = w; grid.appendChild(el);
                });

                var firstOfMonth = new Date(view.getFullYear(), view.getMonth(), 1);
                var startOffset = firstOfMonth.getDay();
                var daysInMonth = new Date(view.getFullYear(), view.getMonth() + 1, 0).getDate();
                var today = new Date();

                for (var i = 0; i < startOffset; i++) {
                    var prevMonthLast = new Date(view.getFullYear(), view.getMonth(), 0).getDate();
                    var dNum = prevMonthLast - startOffset + i + 1;
                    var cell = document.createElement('button');
                    cell.type = 'button'; cell.className = 'dtp-day dtp-outside'; cell.textContent = dNum;
                    (function (offset) {
                        cell.onclick = function () { view.setMonth(view.getMonth() - 1); render(); };
                    })(1);
                    grid.appendChild(cell);
                }
                for (var day = 1; day <= daysInMonth; day++) {
                    var cellDate = new Date(view.getFullYear(), view.getMonth(), day);
                    var btn = document.createElement('button');
                    btn.type = 'button'; btn.className = 'dtp-day'; btn.textContent = day;
                    if (selected && cellDate.getFullYear() === selected.getFullYear() && cellDate.getMonth() === selected.getMonth() && cellDate.getDate() === selected.getDate()) {
                        btn.classList.add('dtp-selected');
                    } else if (cellDate.toDateString() === today.toDateString()) {
                        btn.classList.add('dtp-today');
                    }
                    (function (dt) {
                        btn.onclick = function () {
                            setInputValue(input, formatIsoDate(dt));
                            refreshTrigger(input);
                            closeOpenPanel();
                        };
                    })(cellDate);
                    grid.appendChild(btn);
                }
            } else if (mode === 'months') {
                title.textContent = view.getFullYear() + '年';
                title.onclick = function () { mode = 'years'; render(); };
                prevBtn.onclick = function () { view.setFullYear(view.getFullYear() - 1); render(); };
                nextBtn.onclick = function () { view.setFullYear(view.getFullYear() + 1); render(); };
                grid.classList.add('dtp-months');
                MONTH_LABELS.forEach(function (label, idx) {
                    var cell = document.createElement('button');
                    cell.type = 'button'; cell.className = 'dtp-cell';
                    if (idx === view.getMonth()) cell.classList.add('dtp-selected');
                    cell.textContent = label;
                    cell.onclick = function () { view.setMonth(idx); mode = 'days'; render(); };
                    grid.appendChild(cell);
                });
            } else { // years
                title.textContent = yearPageStart + ' – ' + (yearPageStart + 11);
                title.onclick = function () { mode = 'months'; render(); };
                prevBtn.onclick = function () { yearPageStart -= 12; render(); };
                nextBtn.onclick = function () { yearPageStart += 12; render(); };
                grid.classList.add('dtp-years');
                for (var y = yearPageStart; y < yearPageStart + 12; y++) {
                    var ycell = document.createElement('button');
                    ycell.type = 'button'; ycell.className = 'dtp-cell';
                    if (y === view.getFullYear()) ycell.classList.add('dtp-selected');
                    ycell.textContent = y;
                    (function (yy) { ycell.onclick = function () { view.setFullYear(yy); mode = 'months'; render(); }; })(y);
                    grid.appendChild(ycell);
                }
            }
            panel.appendChild(grid);

            var footer = document.createElement('div');
            footer.className = 'dtp-footer';
            var todayBtn = document.createElement('button');
            todayBtn.type = 'button'; todayBtn.className = 'dtp-today-btn'; todayBtn.textContent = '今天';
            todayBtn.onclick = function () {
                var t = new Date();
                setInputValue(input, formatIsoDate(t));
                refreshTrigger(input);
                closeOpenPanel();
            };
            var clearBtn = document.createElement('button');
            clearBtn.type = 'button'; clearBtn.className = 'dtp-clear-btn'; clearBtn.textContent = '清除';
            clearBtn.onclick = function () {
                setInputValue(input, '');
                refreshTrigger(input);
                closeOpenPanel();
            };
            footer.appendChild(todayBtn); footer.appendChild(clearBtn);
            panel.appendChild(footer);
        }

        render();
        positionPanel(panel, trigger);
        trigger.classList.add('dtp-open');
        openPanel = panel; openTrigger = trigger;
    }

    // ---------------- 時間面板 ----------------
    function openTimePanel(trigger, input) {
        closeOpenPanel();
        var t = parseTime(input.value) || { h: null, m: null };

        var panel = document.createElement('div');
        panel.className = 'dtp-panel dtp-time';

        var cols = document.createElement('div');
        cols.className = 'dtp-time-cols';

        var hourCol = document.createElement('div'); hourCol.className = 'dtp-time-col';
        var sep = document.createElement('div'); sep.className = 'dtp-time-sep'; sep.textContent = ':';
        var minCol = document.createElement('div'); minCol.className = 'dtp-time-col';

        function commit(h, m) {
            if (h == null || m == null) return;
            setInputValue(input, pad2(h) + ':' + pad2(m));
            refreshTrigger(input);
        }

        for (var h = 0; h < 24; h++) {
            var hCell = document.createElement('div');
            hCell.className = 'dtp-time-cell'; hCell.textContent = pad2(h);
            if (h === t.h) hCell.classList.add('dtp-selected');
            (function (hh, el) {
                el.onclick = function () {
                    hourCol.querySelectorAll('.dtp-selected').forEach(function (n) { n.classList.remove('dtp-selected'); });
                    el.classList.add('dtp-selected');
                    t.h = hh;
                    if (t.m == null) t.m = 0;
                    commit(t.h, t.m);
                };
            })(h, hCell);
            hourCol.appendChild(hCell);
        }
        for (var m = 0; m < 60; m++) {
            var mCell = document.createElement('div');
            mCell.className = 'dtp-time-cell'; mCell.textContent = pad2(m);
            if (m === t.m) mCell.classList.add('dtp-selected');
            (function (mm, el) {
                el.onclick = function () {
                    minCol.querySelectorAll('.dtp-selected').forEach(function (n) { n.classList.remove('dtp-selected'); });
                    el.classList.add('dtp-selected');
                    t.m = mm;
                    if (t.h == null) t.h = 0;
                    commit(t.h, t.m);
                };
            })(m, mCell);
            minCol.appendChild(mCell);
        }

        cols.appendChild(hourCol); cols.appendChild(sep); cols.appendChild(minCol);
        panel.appendChild(cols);

        var footer = document.createElement('div');
        footer.className = 'dtp-footer';
        // 使用者要求: 時間面板的「現在」改成「清除」、原本的「清除」改成「確認」——
        // 左邊按鈕變成清空這個欄位, 右邊按鈕變成單純把目前選好的時/分收下、關閉面板 (不改值,
        // 因為點小時/分鐘的格子時 commit() 已經即時寫回 input 了, 這裡「確認」只是關閉面板的意思)。
        var clearBtn = document.createElement('button');
        clearBtn.type = 'button'; clearBtn.className = 'dtp-today-btn'; clearBtn.textContent = '清除';
        clearBtn.onclick = function () {
            setInputValue(input, '');
            refreshTrigger(input);
            closeOpenPanel();
        };
        var confirmBtn = document.createElement('button');
        confirmBtn.type = 'button'; confirmBtn.className = 'dtp-clear-btn'; confirmBtn.textContent = '確認';
        confirmBtn.onclick = function () {
            closeOpenPanel();
        };
        footer.appendChild(clearBtn); footer.appendChild(confirmBtn);
        panel.appendChild(footer);

        positionPanel(panel, trigger);
        // 打開時捲動到目前選到的位置 (置中), 沒有選過就停在開頭
        [[hourCol, t.h], [minCol, t.m]].forEach(function (pair) {
            var col = pair[0], val = pair[1];
            if (val == null) return;
            var cell = col.children[val];
            if (cell) col.scrollTop = cell.offsetTop - col.clientHeight / 2 + cell.offsetHeight / 2;
        });

        trigger.classList.add('dtp-open');
        openPanel = panel; openTrigger = trigger;
    }

    // ---------------- 觸發按鈕 ----------------
    function refreshTrigger(input) {
        var trigger = input.__dtpTrigger;
        if (!trigger) return;
        var valueEl = trigger.querySelector('.dtp-value');
        var isDate = input.type === 'date' || input.getAttribute('data-dtp-type') === 'date';
        if (isDate) {
            var d = parseIsoDate(input.value);
            if (d) { valueEl.textContent = formatDateDisplay(d); valueEl.classList.remove('dtp-placeholder'); }
            else { valueEl.textContent = input.placeholder || '選擇日期'; valueEl.classList.add('dtp-placeholder'); }
        } else {
            var t = parseTime(input.value);
            if (t) { valueEl.textContent = pad2(t.h) + ':' + pad2(t.m); valueEl.classList.remove('dtp-placeholder'); }
            else { valueEl.textContent = input.placeholder || '選擇時間'; valueEl.classList.add('dtp-placeholder'); }
        }
        trigger.disabled = !!input.disabled;
    }

    function enhance(input) {
        if (input.__dtpEnhanced) { refreshTrigger(input); return; }
        if (input.hasAttribute('data-dtp-skip')) return;
        var isDate = input.type === 'date';
        var isTime = input.type === 'time';
        if (!isDate && !isTime) return;

        // 使用者回報「新增航班會多顯示」「時間被蓋住」「填寫完後無法修改」——根因是這個網站有幾個地方
        // (例如 itinerary/new.html 的「+新增航段」) 用 cloneNode(true) 複製一整列表單當範本再清空欄位。
        // 複製節點會把這支腳本先前建立的 wrap span + trigger 按鈕的「DOM 結構」也一起複製過去, 但
        // __dtpEnhanced 這種 runtime 屬性、以及用 addEventListener 掛的 click 事件監聽器都不會被複製——
        // 如果不處理, 之後 scan() 掃到這個複製出來的 input (沒有 __dtpEnhanced 標記) 又會在外面再包一層
        // 新的 wrap/trigger, 疊出兩個按鈕、位置也會跑掉, 而複製過來的那個舊按鈕因為監聽器沒了、點了也
        // 沒反應, 看起來就像「填寫完後無法修改」。這裡偵測到父層已經是這支腳本建立的 wrap (用
        // dtp-input-wrap 這個 class 認, 不用靠 runtime 屬性), 就先把 input 還原、把整個舊 wrap 連同裡面
        // 那顆失效的舊按鈕一起拆掉, 保證每個 input 最後一定只會被包成乾淨的一份。
        var staleWrap = input.parentElement && input.parentElement.classList.contains('dtp-input-wrap')
            ? input.parentElement : null;
        if (staleWrap && staleWrap.parentElement) {
            staleWrap.parentElement.insertBefore(input, staleWrap);
            staleWrap.parentElement.removeChild(staleWrap);
            input.style.position = ''; input.style.inset = ''; input.style.width = '';
            input.style.height = ''; input.style.opacity = ''; input.style.pointerEvents = '';
            input.tabIndex = 0;
            delete input.__dtpTrigger;
        }

        input.__dtpEnhanced = true;

        var wrap = document.createElement('span');
        wrap.className = 'dtp-input-wrap';
        wrap.style.position = 'relative';
        // 沿用原本 input 的版面配置, 讓觸發按鈕接手同樣的寬度/排版, 不用一一調整既有的排版:
        //   1. 行內 style="width:..." 直接照抄。
        //   2. 沒有行內寬度的話, 比較這個 input 原本佔滿的寬度是不是幾乎等於它父層容器的寬度
        //      (例如某個表單欄位的 input { width:100% } 這種寫在 CSS 類別、不是行內 style 上的情況)——
        //      如果幾乎一樣寬, 判斷這裡本來就是「想要撐滿整行」的欄位, 用 block+100% 撐開; 不然 (例如
        //      跟其他欄位並排在同一個 flex 行裡的緊湊小欄位) 用 inline-block 讓按鈕跟著內容自然縮放,
        //      不要把旁邊排好的其他欄位擠開。
        var rect = input.getBoundingClientRect();
        var parentRect = input.parentNode.getBoundingClientRect();
        var looksFullWidth = parentRect.width > 0 && (parentRect.width - rect.width) < 24;
        if (input.style.width) { wrap.style.display = 'inline-block'; wrap.style.width = input.style.width; }
        else if (looksFullWidth) { wrap.style.display = 'block'; wrap.style.width = '100%'; }
        else {
            // 使用者回報「出發時間跟抵達機場蓋在一起」——即使是「還沒點開面板」的靜止狀態就已經蓋住了,
            // 跟彈出面板無關。這裡原本是 inline-block 不特別設寬度, 讓按鈕照 CSS flex 內容自然決定寬度,
            // 但在 itinerary/new.html「出發時間」這種夾在 CSS Grid 固定欄寬(120px)裡的緊湊欄位, 瀏覽器排版
            // 這種「auto 寬度」的行內區塊時, 如果裡面的內容(圖示+文字)有任何一點無法完全壓縮到欄寬以內
            // (例如某些瀏覽器判斷 flex 子元素的預設「內容最小寬度」時沒有完全套用 overflow:hidden 應該歸零
            // 的規則), 這顆 span 最終算出來的寬度就可能比原本這個 input 實際佔的欄寬還寬, 於是整顆觸發按鈕
            // 就會比 120px 的欄位還寬、往右邊侵佔到下一欄「抵達機場」。改成直接把原本這個 input 量到的實際
            // 寬度(px)釘死在 wrap 上, 不管瀏覽器怎麼計算内容最小寬度, 觸發按鈕都不可能超出原本這個欄位量到
            // 的寬度, 徹底排除這種「比原本欄位還寬」的可能性。
            wrap.style.display = 'inline-block';
            wrap.style.width = rect.width + 'px';
        }

        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);

        var trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'dtp-trigger';
        var icon = document.createElement('i');
        icon.className = 'dtp-icon ' + (isDate ? 'fa-regular fa-calendar' : 'fa-regular fa-clock');
        var valueEl = document.createElement('span');
        valueEl.className = 'dtp-value';
        trigger.appendChild(icon);
        trigger.appendChild(valueEl);
        wrap.appendChild(trigger);

        // 原生 input 疊在按鈕正下方、視覺上隱形, 但不是 display:none —— 保留 required/pattern 這些原生
        // 表單驗證的行為, 瀏覽器要跳原生驗證提示氣泡時, 位置也還是貼著這個按鈕 (兩者疊在同一個位置)。
        input.style.position = 'absolute';
        input.style.inset = '0';
        input.style.width = '100%';
        input.style.height = '100%';
        input.style.opacity = '0';
        input.style.pointerEvents = 'none';
        input.tabIndex = -1;

        input.__dtpTrigger = trigger;
        refreshTrigger(input);

        trigger.addEventListener('click', function () {
            if (input.disabled) return;
            if (openTrigger === trigger) { closeOpenPanel(); return; }
            if (isDate) openDatePanel(trigger, input); else openTimePanel(trigger, input);
        });

        // 有些地方的既有邏輯會在事後才動態改 disabled (目前這三個頁面用到的 date/time input 幾乎都是
        // 渲染當下就決定好 canEdit、不會事後切換, 這裡多做一次保險同步, 成本很低)。
        var obs = new MutationObserver(function () { refreshTrigger(input); });
        obs.observe(input, { attributes: true, attributeFilter: ['disabled', 'value', 'placeholder'] });
    }

    function scan(root) {
        (root || document).querySelectorAll('input[type="date"], input[type="time"]').forEach(enhance);
    }

    // 監看整個文件, 任何時候有新的 date/time input 被插進 DOM (innerHTML 換內容、範本字串插入清單...)
    // 就自動套用, 不用去改動每一個重新渲染的地方。用短暫的 debounce 避免密集 DOM 異動時重複掃描整份文件。
    var scanTimer = null;
    function scheduleScan() {
        if (scanTimer) return;
        scanTimer = setTimeout(function () { scanTimer = null; scan(document); }, 40);
    }
    var mo = new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
            if (mutations[i].addedNodes && mutations[i].addedNodes.length > 0) { scheduleScan(); return; }
        }
    });

    function start() {
        scan(document);
        mo.observe(document.documentElement, { childList: true, subtree: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }

    window.DTP = { scan: scan, enhance: enhance };
})();