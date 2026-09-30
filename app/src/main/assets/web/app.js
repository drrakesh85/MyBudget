const API_BASE = '';
let currentTxnPage = 1;
let cachedAccounts = [];
let cachedCategories = {};
let cachedPayees = [];

let selectedDatePreset = 'ALL';
let customFromDate = '';
let customToDate = '';
let selectedTypes = [];
let selectedAccounts = [];
let selectedCategories = [];
let selectedSubcategories = [];
let selectedPayees = [];

let payeeDebounceTimer = null;
let searchDebounceTimer = null;
let currentSuggestions = [];
let selectedSuggestionIndex = -1;

document.addEventListener('DOMContentLoaded', () => {
    switchNav('dashboard');
    populateFilters();
});

function switchNav(viewName) {
    document.querySelectorAll('.nav-item').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.querySelector(`.nav-item[onclick*="${viewName}"]`);
    if (activeBtn) activeBtn.classList.add('active');

    document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));
    const targetSection = document.getElementById(`view${capitalize(viewName)}`);
    if (targetSection) targetSection.classList.add('active');

    document.getElementById('pageTitle').textContent = capitalize(viewName);

    if (viewName === 'dashboard') loadDashboard();
    if (viewName === 'accounts') loadAccounts();
    if (viewName === 'transactions') {
        updateFilterUI();
        loadTransactions(1);
    }
    if (viewName === 'categories') loadCategories();
}

function capitalize(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
}

// 1. Dashboard View
async function loadDashboard() {
    try {
        const res = await fetch('/api/dashboard');
        if (!res.ok) throw new Error();
        const data = await res.json();

        document.getElementById('netWorthVal').textContent = formatCurrency(data.netWorth);
        document.getElementById('totalAccountsVal').textContent = data.totalAccounts;

        if (data.today) {
            document.getElementById('todayIncome').textContent = formatCurrency(data.today.first);
            document.getElementById('todayExpense').textContent = formatCurrency(data.today.second);
            document.getElementById('todayNet').textContent = formatCurrency(data.today.third);
        }
        if (data.thisWeek) {
            document.getElementById('weekIncome').textContent = formatCurrency(data.thisWeek.first);
            document.getElementById('weekExpense').textContent = formatCurrency(data.thisWeek.second);
            document.getElementById('weekNet').textContent = formatCurrency(data.thisWeek.third);
        }
        if (data.thisMonth) {
            document.getElementById('monthIncome').textContent = formatCurrency(data.thisMonth.first);
            document.getElementById('monthExpense').textContent = formatCurrency(data.thisMonth.second);
            document.getElementById('monthNet').textContent = formatCurrency(data.thisMonth.third);
        }
        if (data.yearToDate) {
            document.getElementById('yearIncome').textContent = formatCurrency(data.yearToDate.first);
            document.getElementById('yearExpense').textContent = formatCurrency(data.yearToDate.second);
            document.getElementById('yearNet').textContent = formatCurrency(data.yearToDate.third);
        }
    } catch (e) {
        console.error('Failed to load dashboard', e);
    }
}

// 2. Accounts View
async function loadAccounts() {
    try {
        const res = await fetch('/api/accounts');
        if (!res.ok) throw new Error();
        cachedAccounts = await res.json();

        const tbody = document.getElementById('accountsTableBody');
        tbody.innerHTML = '';

        cachedAccounts.forEach(acc => {
            const tr = document.createElement('tr');
            const statusTag = acc.isHidden
                ? '<span class="status-tag hidden">Hidden</span>'
                : '<span class="status-tag visible">Visible</span>';

            tr.innerHTML = `
                <td><strong>${escapeHtml(acc.nickName)}</strong></td>
                <td><span class="account-type-tag">${escapeHtml(acc.type)}</span></td>
                <td><strong style="color: ${acc.balance >= 0 ? '#2e7d32' : '#c62828'}">${formatCurrency(acc.balance)}</strong></td>
                <td>${statusTag}</td>
                <td>
                    <div class="action-buttons">
                        <button onclick="viewStatement('${escapeJs(acc.nickName)}')" class="btn-action-sm btn-view">Statement</button>
                        <button onclick="openEditAccountModal('${escapeJs(acc.id)}')" class="btn-action-sm btn-edit">Edit</button>
                        <button onclick="toggleAccountVisibility('${escapeJs(acc.nickName)}')" class="btn-action-sm ${acc.isHidden ? 'btn-view' : 'btn-edit'}">${acc.isHidden ? 'Show' : 'Hide'}</button>
                        <button onclick="handleDeleteAccount('${escapeJs(acc.nickName)}')" class="btn-action-sm btn-delete">Delete</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error('Failed to load accounts', e);
    }
}

function openAddAccountModal() {
    document.getElementById('accountModalTitle').textContent = 'Add Account';
    document.getElementById('accountFormId').value = '';
    document.getElementById('accountFormOldNickName').value = '';
    document.getElementById('accountForm').reset();
    document.getElementById('accountFormError').textContent = '';

    document.getElementById('accountFormType').value = 'SAVING';
    onAccountTypeChange();
    document.getElementById('accountModal').style.display = 'flex';
}

function openEditAccountModal(accountId) {
    const acc = cachedAccounts.find(a => a.id === accountId);
    if (!acc) return;

    document.getElementById('accountModalTitle').textContent = 'Edit Account';
    document.getElementById('accountFormId').value = acc.id;
    document.getElementById('accountFormOldNickName').value = acc.nickName;
    document.getElementById('accountFormError').textContent = '';

    document.getElementById('accountFormType').value = acc.type || 'SAVING';
    onAccountTypeChange();

    document.getElementById('accountFormNickName').value = acc.nickName || '';
    document.getElementById('accountFormBankName').value = acc.bankName || '';
    document.getElementById('accountFormBranchName').value = acc.branchName || '';
    document.getElementById('accountFormAccountNumber').value = acc.accountNumber || '';
    document.getElementById('accountFormCardNumber').value = acc.cardNumber || '';
    document.getElementById('accountFormExpiry').value = acc.expiry || '';
    document.getElementById('accountFormCvv').value = ''; // Always leave CVV blank when editing existing accounts
    document.getElementById('accountFormBillingDate').value = acc.billingDate || 1;
    document.getElementById('accountFormDueDate').value = acc.dueDate || 1;
    document.getElementById('accountFormSmsSender').value = acc.smsSenderKeywords || '';
    document.getElementById('accountFormSmsParsing').checked = acc.smsParsingEnabled !== false;

    document.getElementById('accountModal').style.display = 'flex';
}

function closeAccountModal() {
    document.getElementById('accountModal').style.display = 'none';
}

function onAccountTypeChange() {
    const type = document.getElementById('accountFormType').value;

    const grpBankName = document.getElementById('grpBankName');
    const grpBranchName = document.getElementById('grpBranchName');
    const grpAccountNumber = document.getElementById('grpAccountNumber');
    const grpCardNumber = document.getElementById('grpCardNumber');
    const grpExpiry = document.getElementById('grpExpiry');
    const grpCvv = document.getElementById('grpCvv');
    const grpBillingDate = document.getElementById('grpBillingDate');
    const grpDueDate = document.getElementById('grpDueDate');

    if (type === 'SAVING' || type === 'LOAN') {
        grpBankName.style.display = 'block';
        grpBranchName.style.display = 'block';
        grpAccountNumber.style.display = 'block';
        grpCardNumber.style.display = 'none';
        grpExpiry.style.display = 'none';
        grpCvv.style.display = 'none';
        grpBillingDate.style.display = 'none';
        grpDueDate.style.display = 'none';
    } else if (type === 'CREDIT_CARD') {
        grpBankName.style.display = 'block';
        grpBranchName.style.display = 'none';
        grpAccountNumber.style.display = 'none';
        grpCardNumber.style.display = 'block';
        grpExpiry.style.display = 'block';
        grpCvv.style.display = 'block';
        grpBillingDate.style.display = 'block';
        grpDueDate.style.display = 'block';
    } else { // CASH
        grpBankName.style.display = 'none';
        grpBranchName.style.display = 'none';
        grpAccountNumber.style.display = 'none';
        grpCardNumber.style.display = 'none';
        grpExpiry.style.display = 'none';
        grpCvv.style.display = 'none';
        grpBillingDate.style.display = 'none';
        grpDueDate.style.display = 'none';
    }
}

async function handleSaveAccount(e) {
    e.preventDefault();
    const saveBtn = document.getElementById('saveAccountBtn');
    const errorEl = document.getElementById('accountFormError');
    errorEl.textContent = '';
    saveBtn.disabled = true;

    const accountId = document.getElementById('accountFormId').value;
    const oldNickName = document.getElementById('accountFormOldNickName').value;
    const isEdit = Boolean(accountId);

    const type = document.getElementById('accountFormType').value;
    const nickName = document.getElementById('accountFormNickName').value.trim();

    if (!nickName) {
        errorEl.textContent = 'Account nickname is required.';
        saveBtn.disabled = false;
        return;
    }

    if (type === 'CREDIT_CARD') {
        const rawCardNumber = document.getElementById('accountFormCardNumber').value.trim();
        const isPreservingCard = isEdit && (rawCardNumber === '' || rawCardNumber.includes('•'));
        if (!isPreservingCard) {
            const cleanCard = rawCardNumber.replace(/[\s-]/g, '');
            if (!/^\d{13,19}$/.test(cleanCard)) {
                errorEl.textContent = 'Invalid card number. Must contain 13–19 digits.';
                saveBtn.disabled = false;
                return;
            }
        }

        const rawCvv = document.getElementById('accountFormCvv').value.trim();
        const isPreservingCvv = isEdit && rawCvv === '';
        if (!isPreservingCvv) {
            if (!/^\d{3,4}$/.test(rawCvv)) {
                errorEl.textContent = 'Invalid CVV. Must contain 3 or 4 digits.';
                saveBtn.disabled = false;
                return;
            }
        }
    }

    const payload = {
        type: type,
        nickName: nickName,
        bankName: document.getElementById('accountFormBankName').value.trim(),
        branchName: document.getElementById('accountFormBranchName').value.trim(),
        accountNumber: document.getElementById('accountFormAccountNumber').value.trim(),
        cardNumber: document.getElementById('accountFormCardNumber').value.trim(),
        expiry: document.getElementById('accountFormExpiry').value.trim(),
        cvv: document.getElementById('accountFormCvv').value.trim(),
        billingDate: parseInt(document.getElementById('accountFormBillingDate').value) || 1,
        dueDate: parseInt(document.getElementById('accountFormDueDate').value) || 1,
        smsSenderKeywords: document.getElementById('accountFormSmsSender').value.trim(),
        smsParsingEnabled: document.getElementById('accountFormSmsParsing').checked
    };

    const url = isEdit ? `/api/accounts/${encodeURIComponent(oldNickName)}` : '/api/accounts';
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const res = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json();
        if (res.ok) {
            closeAccountModal();
            refreshAllActiveViews();
        } else {
            errorEl.textContent = data.error || 'Failed to save account.';
        }
    } catch (err) {
        errorEl.textContent = 'Server connection error.';
    } finally {
        saveBtn.disabled = false;
    }
}

async function toggleAccountVisibility(accountName) {
    try {
        const res = await fetch(`/api/accounts/${encodeURIComponent(accountName)}/toggle-visibility`, {
            method: 'PUT'
        });

        if (res.ok) {
            refreshAllActiveViews();
        } else {
            const data = await res.json();
            alert('Failed to toggle visibility: ' + (data.error || 'Unknown error'));
        }
    } catch (e) {
        alert('Failed to connect to server.');
    }
}

async function handleDeleteAccount(accountName) {
    if (!confirm(`Are you sure you want to delete or hide account '${accountName}'?`)) return;

    try {
        const res = await fetch(`/api/accounts/${encodeURIComponent(accountName)}`, {
            method: 'DELETE'
        });

        const data = await res.json();
        if (res.ok) {
            if (data.message) {
                alert(data.message);
            }
            refreshAllActiveViews();
        } else {
            alert('Failed to delete account: ' + (data.error || 'Unknown error'));
        }
    } catch (e) {
        alert('Failed to connect to server.');
    }
}

// 3. Account Statement View & Multi-Select Filters
let currentStmtAccountName = '';
let cachedStmtData = null;
let stmtSelectedDatePreset = 'ALL';
let stmtCustomFromDate = '';
let stmtCustomToDate = '';
let stmtSelectedTypes = [];
let stmtSelectedCategories = [];
let stmtSelectedSubcategories = [];
let stmtSelectedPayees = [];

async function viewStatement(accountName) {
    document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));
    document.getElementById('viewStatement').classList.add('active');
    document.getElementById('pageTitle').textContent = 'Account Statement';

    if (currentStmtAccountName !== accountName) {
        currentStmtAccountName = accountName;
        stmtSelectedDatePreset = 'ALL';
        stmtCustomFromDate = '';
        stmtCustomToDate = '';
        stmtSelectedTypes = [];
        stmtSelectedCategories = [];
        stmtSelectedSubcategories = [];
        stmtSelectedPayees = [];
    }

    try {
        const res = await fetch(`/api/accounts/${encodeURIComponent(accountName)}/statement`);
        if (!res.ok) throw new Error();
        cachedStmtData = await res.json();

        document.getElementById('stmtAccountName').textContent = cachedStmtData.accountName;
        document.getElementById('stmtAccountType').textContent = cachedStmtData.accountType;
        document.getElementById('stmtBalanceVal').textContent = formatCurrency(cachedStmtData.currentBalance);

        populateStmtFilterOptions();
        renderStmtTransactions();
    } catch (e) {
        console.error('Failed to load statement', e);
    }
}

function populateStmtFilterOptions() {
    if (!cachedStmtData || !cachedStmtData.transactions) return;

    renderStmtCategoryPopoverItems();
    renderStmtSubcategoryPopoverItems();
    renderStmtPayeePopoverItems();
    updateStmtFilterUI();
}

function renderStmtCategoryPopoverItems() {
    const container = document.getElementById('listStmtCategoryItems');
    if (!container || !cachedStmtData || !cachedStmtData.transactions) return;
    container.innerHTML = '';

    const categories = Array.from(new Set(cachedStmtData.transactions.map(t => t.category).filter(Boolean))).sort();
    categories.forEach(cat => {
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = stmtSelectedCategories.includes(cat);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(cat)}" ${isChecked ? 'checked' : ''} onchange="onStmtCategoryFilterCheckboxChanged(this)">
            <span>${escapeHtml(cat)}</span>
        `;
        container.appendChild(label);
    });
}

function renderStmtSubcategoryPopoverItems() {
    const container = document.getElementById('listStmtSubcategoryItems');
    if (!container || !cachedStmtData || !cachedStmtData.transactions) return;
    container.innerHTML = '';

    let txns = cachedStmtData.transactions;
    if (stmtSelectedCategories.length > 0) {
        txns = txns.filter(t => stmtSelectedCategories.includes(t.category));
    }

    const availableSubs = Array.from(new Set(txns.map(t => t.subcategory).filter(Boolean))).sort();

    const noSubLabel = document.createElement('label');
    noSubLabel.className = 'popover-option';
    const isNoSubChecked = stmtSelectedSubcategories.includes('__NO_SUBCATEGORY__');
    noSubLabel.innerHTML = `
        <input type="checkbox" value="__NO_SUBCATEGORY__" ${isNoSubChecked ? 'checked' : ''} onchange="onStmtSubcategoryFilterCheckboxChanged(this)">
        <span style="font-style: italic; color: #64748b;">[No Subcategory]</span>
    `;
    container.appendChild(noSubLabel);

    availableSubs.forEach(sub => {
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = stmtSelectedSubcategories.includes(sub);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(sub)}" ${isChecked ? 'checked' : ''} onchange="onStmtSubcategoryFilterCheckboxChanged(this)">
            <span>${escapeHtml(sub)}</span>
        `;
        container.appendChild(label);
    });

    stmtSelectedSubcategories = stmtSelectedSubcategories.filter(s => s === '__NO_SUBCATEGORY__' || availableSubs.includes(s));
}

function renderStmtPayeePopoverItems() {
    const container = document.getElementById('listStmtPayeeItems');
    if (!container || !cachedStmtData || !cachedStmtData.transactions) return;
    container.innerHTML = '';

    const payees = Array.from(new Set(cachedStmtData.transactions.map(t => t.payeePayer).filter(Boolean))).sort();
    payees.forEach(payee => {
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = stmtSelectedPayees.includes(payee);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(payee)}" ${isChecked ? 'checked' : ''} onchange="onStmtPayeeFilterCheckboxChanged(this)">
            <span>${escapeHtml(payee)}</span>
        `;
        container.appendChild(label);
    });
}

function onStmtTypeFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!stmtSelectedTypes.includes(cb.value)) stmtSelectedTypes.push(cb.value);
    } else {
        stmtSelectedTypes = stmtSelectedTypes.filter(v => v !== cb.value);
    }
    updateStmtFilterUI();
    renderStmtTransactions();
}

function onStmtCategoryFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!stmtSelectedCategories.includes(cb.value)) stmtSelectedCategories.push(cb.value);
    } else {
        stmtSelectedCategories = stmtSelectedCategories.filter(v => v !== cb.value);
    }
    renderStmtSubcategoryPopoverItems();
    updateStmtFilterUI();
    renderStmtTransactions();
}

function onStmtSubcategoryFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!stmtSelectedSubcategories.includes(cb.value)) stmtSelectedSubcategories.push(cb.value);
    } else {
        stmtSelectedSubcategories = stmtSelectedSubcategories.filter(v => v !== cb.value);
    }
    updateStmtFilterUI();
    renderStmtTransactions();
}

function onStmtPayeeFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!stmtSelectedPayees.includes(cb.value)) stmtSelectedPayees.push(cb.value);
    } else {
        stmtSelectedPayees = stmtSelectedPayees.filter(v => v !== cb.value);
    }
    updateStmtFilterUI();
    renderStmtTransactions();
}

function selectAllStmtPopoverItems(filterType, event) {
    if (event) { event.preventDefault(); event.stopPropagation(); }
    if (!cachedStmtData || !cachedStmtData.transactions) return;

    if (filterType === 'Type') {
        stmtSelectedTypes = ['Expense', 'Income', 'Transfer'];
        document.querySelectorAll('#listStmtTypeItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    } else if (filterType === 'Category') {
        stmtSelectedCategories = Array.from(new Set(cachedStmtData.transactions.map(t => t.category).filter(Boolean))).sort();
        document.querySelectorAll('#listStmtCategoryItems input[type="checkbox"]').forEach(cb => cb.checked = true);
        renderStmtSubcategoryPopoverItems();
    } else if (filterType === 'Subcategory') {
        let availableSubs = ['__NO_SUBCATEGORY__'];
        let txns = cachedStmtData.transactions;
        if (stmtSelectedCategories.length > 0) {
            txns = txns.filter(t => stmtSelectedCategories.includes(t.category));
        }
        txns.forEach(t => { if (t.subcategory) availableSubs.push(t.subcategory); });
        stmtSelectedSubcategories = Array.from(new Set(availableSubs)).sort();
        document.querySelectorAll('#listStmtSubcategoryItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    } else if (filterType === 'Payee') {
        stmtSelectedPayees = Array.from(new Set(cachedStmtData.transactions.map(t => t.payeePayer).filter(Boolean))).sort();
        document.querySelectorAll('#listStmtPayeeItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    }
    updateStmtFilterUI();
    renderStmtTransactions();
}

function clearStmtPopoverItems(filterType, event) {
    if (event) { event.preventDefault(); event.stopPropagation(); }
    if (filterType === 'Type') {
        stmtSelectedTypes = [];
        document.querySelectorAll('#listStmtTypeItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    } else if (filterType === 'Category') {
        stmtSelectedCategories = [];
        document.querySelectorAll('#listStmtCategoryItems input[type="checkbox"]').forEach(cb => cb.checked = false);
        renderStmtSubcategoryPopoverItems();
    } else if (filterType === 'Subcategory') {
        stmtSelectedSubcategories = [];
        document.querySelectorAll('#listStmtSubcategoryItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    } else if (filterType === 'Payee') {
        stmtSelectedPayees = [];
        document.querySelectorAll('#listStmtPayeeItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    }
    updateStmtFilterUI();
    renderStmtTransactions();
}

function filterStmtPopoverList(filterType, searchVal) {
    const query = searchVal.trim().toLowerCase();
    const containerId = `listStmt${filterType}Items`;
    const container = document.getElementById(containerId);
    if (!container) return;

    container.querySelectorAll('.popover-option').forEach(label => {
        const text = label.textContent.toLowerCase();
        label.style.display = text.includes(query) ? 'flex' : 'none';
    });
}

function parseTxnDateToMillis(dateStr) {
    if (!dateStr) return 0;
    const str = dateStr.trim();
    const dmyMatch = str.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
    if (dmyMatch) {
        const day = parseInt(dmyMatch[1], 10);
        const month = parseInt(dmyMatch[2], 10) - 1;
        const year = parseInt(dmyMatch[3], 10);
        return new Date(year, month, day, 0, 0, 0, 0).getTime();
    }
    const ymdMatch = str.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);
    if (ymdMatch) {
        const year = parseInt(ymdMatch[1], 10);
        const month = parseInt(ymdMatch[2], 10) - 1;
        const day = parseInt(ymdMatch[3], 10);
        return new Date(year, month, day, 0, 0, 0, 0).getTime();
    }
    const d = new Date(str);
    return isNaN(d.getTime()) ? 0 : d.getTime();
}

function calculateDateRangeMillis(preset, customFromStr, customToStr) {
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0).getTime();
    const todayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).getTime();

    if (preset === 'TODAY' || preset === 'Today') {
        return { start: todayStart, end: todayEnd, isValid: true };
    }

    if (preset === 'THIS_WEEK' || preset === 'This Week') {
        const dayOfWeek = now.getDay();
        const distToMonday = (dayOfWeek + 6) % 7;
        const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - distToMonday, 0, 0, 0, 0);
        const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6, 23, 59, 59, 999);
        return { start: monday.getTime(), end: sunday.getTime(), isValid: true };
    }

    if (preset === 'THIS_MONTH' || preset === 'This Month') {
        const monthStart = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0);
        const nextMonthStart = new Date(now.getFullYear(), now.getMonth() + 1, 1, 0, 0, 0, 0);
        const monthEnd = new Date(nextMonthStart.getTime() - 1);
        return { start: monthStart.getTime(), end: monthEnd.getTime(), isValid: true };
    }

    if (preset === 'THIS_YEAR' || preset === 'This Year') {
        const yearStart = new Date(now.getFullYear(), 0, 1, 0, 0, 0, 0);
        const yearEnd = new Date(now.getFullYear(), 11, 31, 23, 59, 59, 999);
        return { start: yearStart.getTime(), end: yearEnd.getTime(), isValid: true };
    }

    if (preset === 'CUSTOM' || preset === 'Custom') {
        if (!customFromStr || !customToStr) {
            return { start: null, end: null, isValid: false, error: 'Please select both From and To dates.' };
        }
        const fromMillis = parseTxnDateToMillis(customFromStr);
        const toMillis = parseTxnDateToMillis(customToStr);
        if (!fromMillis || !toMillis) {
            return { start: null, end: null, isValid: false, error: 'Invalid date format.' };
        }
        if (fromMillis > toMillis) {
            return { start: null, end: null, isValid: false, error: 'From Date cannot be after To Date.' };
        }
        const toEndMillis = toMillis + (24 * 60 * 60 * 1000 - 1);
        return { start: fromMillis, end: toEndMillis, isValid: true };
    }

    return { start: null, end: null, isValid: true };
}

function updateDateTriggerText(triggerId, preset) {
    const trigger = document.getElementById(triggerId);
    if (!trigger) return;

    let text = 'Date: All Till Date';
    let isPresetActive = false;

    if (preset === 'TODAY') { text = 'Date: Today'; isPresetActive = true; }
    else if (preset === 'THIS_WEEK') { text = 'Date: This Week'; isPresetActive = true; }
    else if (preset === 'THIS_MONTH') { text = 'Date: This Month'; isPresetActive = true; }
    else if (preset === 'THIS_YEAR') { text = 'Date: This Year'; isPresetActive = true; }
    else if (preset === 'CUSTOM') { text = 'Date: Custom'; isPresetActive = true; }

    const firstSpan = trigger.querySelector('span:not(.arrow)');
    if (firstSpan) {
        firstSpan.textContent = text;
    } else {
        trigger.innerHTML = `<span>${escapeHtml(text)}</span> <span class="arrow">▾</span>`;
    }

    if (isPresetActive) {
        trigger.classList.add('has-selections', 'has-selection');
    } else {
        trigger.classList.remove('has-selections', 'has-selection');
    }
}

function onStmtDatePresetChanged(val) {
    stmtSelectedDatePreset = val;
    const box = document.getElementById('boxStmtCustomDate');
    const err = document.getElementById('errStmtCustomDate');
    if (val === 'CUSTOM') {
        if (box) box.style.display = 'flex';
        onStmtCustomDateChanged();
    } else {
        if (box) box.style.display = 'none';
        if (err) { err.style.display = 'none'; err.textContent = ''; }
        updateStmtFilterUI();
        renderStmtTransactions();
    }
}

function onStmtCustomDateChanged() {
    const fromInput = document.getElementById('inputStmtFromDate');
    const toInput = document.getElementById('inputStmtToDate');
    stmtCustomFromDate = fromInput ? fromInput.value : '';
    stmtCustomToDate = toInput ? toInput.value : '';

    const range = calculateDateRangeMillis('CUSTOM', stmtCustomFromDate, stmtCustomToDate);
    const err = document.getElementById('errStmtCustomDate');
    if (!range.isValid) {
        if (err && range.error) {
            err.style.display = 'block';
            err.textContent = range.error;
        }
        updateStmtFilterUI();
        return;
    }
    if (err) {
        err.style.display = 'none';
        err.textContent = '';
    }
    updateStmtFilterUI();
    renderStmtTransactions();
}

function onDatePresetChanged(val) {
    selectedDatePreset = val;
    const box = document.getElementById('boxCustomDate');
    const err = document.getElementById('errCustomDate');
    if (val === 'CUSTOM') {
        if (box) box.style.display = 'flex';
        onCustomDateChanged();
    } else {
        if (box) box.style.display = 'none';
        if (err) { err.style.display = 'none'; err.textContent = ''; }
        updateFilterUI();
        loadTransactions(1);
    }
}

function onCustomDateChanged() {
    const fromInput = document.getElementById('inputFromDate');
    const toInput = document.getElementById('inputToDate');
    customFromDate = fromInput ? fromInput.value : '';
    customToDate = toInput ? toInput.value : '';

    const range = calculateDateRangeMillis('CUSTOM', customFromDate, customToDate);
    const err = document.getElementById('errCustomDate');
    if (!range.isValid) {
        if (err && range.error) {
            err.style.display = 'block';
            err.textContent = range.error;
        }
        updateFilterUI();
        return;
    }
    if (err) {
        err.style.display = 'none';
        err.textContent = '';
    }
    updateFilterUI();
    loadTransactions(1);
}

function clearAllStmtFilters() {
    stmtSelectedDatePreset = 'ALL';
    stmtCustomFromDate = '';
    stmtCustomToDate = '';
    stmtSelectedTypes = [];
    stmtSelectedCategories = [];
    stmtSelectedSubcategories = [];
    stmtSelectedPayees = [];

    const allRadio = document.querySelector('input[name="stmtDateOpt"][value="ALL"]');
    if (allRadio) allRadio.checked = true;

    const fromInput = document.getElementById('inputStmtFromDate');
    if (fromInput) fromInput.value = '';
    const toInput = document.getElementById('inputStmtToDate');
    if (toInput) toInput.value = '';

    const box = document.getElementById('boxStmtCustomDate');
    if (box) box.style.display = 'none';
    const err = document.getElementById('errStmtCustomDate');
    if (err) { err.style.display = 'none'; err.textContent = ''; }

    document.querySelectorAll('#viewStatement .popover-panel input[type="checkbox"]').forEach(cb => cb.checked = false);

    renderStmtSubcategoryPopoverItems();
    updateStmtFilterUI();
    renderStmtTransactions();
}

function updateStmtFilterUI() {
    updateDateTriggerText('btnStmtDateTrigger', stmtSelectedDatePreset);
    updateTriggerText('btnStmtTypeTrigger', stmtSelectedTypes, 'Type');
    updateTriggerText('btnStmtCategoryTrigger', stmtSelectedCategories, 'Category');

    const subDisplayNames = stmtSelectedSubcategories.map(s => (s === '__NO_SUBCATEGORY__' || s === 'No Subcategory' || s === '[No Subcategory]') ? 'No Subcategory' : s);
    updateTriggerText('btnStmtSubcategoryTrigger', subDisplayNames, 'Subcategory');

    updateTriggerText('btnStmtPayeeTrigger', stmtSelectedPayees, 'Payee / Payer');

    const hasActiveFilters = stmtSelectedDatePreset !== 'ALL' ||
        stmtSelectedTypes.length > 0 ||
        stmtSelectedCategories.length > 0 ||
        stmtSelectedSubcategories.length > 0 ||
        stmtSelectedPayees.length > 0;

    const clearBtn = document.getElementById('btnClearAllStmtFilters');
    if (clearBtn) {
        clearBtn.style.display = hasActiveFilters ? 'inline-flex' : 'none';
    }
}

function renderStmtTransactions() {
    if (!cachedStmtData || !cachedStmtData.transactions) return;

    const tbody = document.getElementById('statementTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    const dateRange = calculateDateRangeMillis(stmtSelectedDatePreset, stmtCustomFromDate, stmtCustomToDate);

    const isFilterActive = stmtSelectedDatePreset !== 'ALL' ||
        stmtSelectedTypes.length > 0 ||
        stmtSelectedCategories.length > 0 ||
        stmtSelectedSubcategories.length > 0 ||
        stmtSelectedPayees.length > 0;

    const filtered = cachedStmtData.transactions.filter(txn => {
        // Date filter
        if (dateRange.isValid && dateRange.start !== null && dateRange.end !== null) {
            const txMillis = parseTxnDateToMillis(txn.date);
            if (txMillis < dateRange.start || txMillis > dateRange.end) {
                return false;
            }
        } else if (!dateRange.isValid && stmtSelectedDatePreset === 'CUSTOM') {
            return false;
        }
        // Type filter (OR within group, AND between groups)
        if (stmtSelectedTypes.length > 0 && !stmtSelectedTypes.includes(txn.transactionType)) {
            return false;
        }
        // Category filter (OR within group, AND between groups)
        if (stmtSelectedCategories.length > 0 && !stmtSelectedCategories.includes(txn.category)) {
            return false;
        }
        // Subcategory filter (OR within group, AND between groups)
        if (stmtSelectedSubcategories.length > 0) {
            const matchesSub = stmtSelectedSubcategories.some(s => {
                if (s === '__NO_SUBCATEGORY__' || s === 'No Subcategory' || s === '[No Subcategory]') {
                    return !txn.subcategory || txn.subcategory.trim() === '';
                }
                return txn.subcategory === s;
            });
            if (!matchesSub) return false;
        }
        // Payee filter (OR within group, AND between groups)
        if (stmtSelectedPayees.length > 0 && !stmtSelectedPayees.includes(txn.payeePayer)) {
            return false;
        }
        return true;
    });

    const filteredBalMap = {};
    let filteredTotalImpact = 0;

    if (isFilterActive) {
        const sortedAsc = [...filtered].sort((a, b) => {
            const dA = parseTxnDateToMillis(a.date);
            const dB = parseTxnDateToMillis(b.date);
            if (dA !== dB) return dA - dB;
            if (a.time !== b.time) return (a.time || '').localeCompare(b.time || '');
            return (a.rowId || '').localeCompare(b.rowId || '');
        });

        let runningFilt = 0.0;
        sortedAsc.forEach(txn => {
            let sem = 0.0;
            if (txn.semanticAmount !== undefined) {
                sem = txn.semanticAmount;
            } else {
                const absVal = Math.abs(txn.amount);
                if (txn.transactionType === 'Transfer') {
                    sem = (txn.toAccount === cachedStmtData.accountName) ? absVal : -absVal;
                } else if (txn.transactionType === 'Income') {
                    sem = txn.amount < 0 ? -absVal : absVal;
                } else if (txn.transactionType === 'Expense') {
                    sem = -absVal;
                } else {
                    sem = txn.amount >= 0 ? absVal : -absVal;
                }
            }
            runningFilt += sem;
            filteredBalMap[txn.rowId] = runningFilt;
        });
        filteredTotalImpact = runningFilt;
    }

    filtered.forEach(txn => {
        const isPos = txn.semanticAmount !== undefined ? txn.semanticAmount >= 0 : (txn.amount >= 0 || txn.transactionType === 'Income');
        const tr = document.createElement('tr');

        let balanceHtml = `<strong>${formatCurrency(txn.runningBalance)}</strong>`;
        if (isFilterActive && filteredBalMap[txn.rowId] !== undefined) {
            const fBal = filteredBalMap[txn.rowId];
            const fColor = fBal >= 0 ? '#2e7d32' : '#c62828';
            balanceHtml = `
                <div style="display: flex; flex-direction: column; gap: 0.15rem;">
                    <span style="font-size: 0.8rem; color: #475569;">Account: <strong>${formatCurrency(txn.runningBalance)}</strong></span>
                    <span style="font-size: 0.8rem; color: #0288d1; font-weight: 700;">Filtered: <strong style="color: ${fColor}">${formatCurrency(fBal)}</strong></span>
                </div>
            `;
        }

        tr.innerHTML = `
            <td>${escapeHtml(txn.date)} ${escapeHtml(txn.time)}</td>
            <td>${escapeHtml(txn.description)}</td>
            <td>${escapeHtml(txn.category || '—')}</td>
            <td>${escapeHtml(txn.subcategory || '—')}</td>
            <td><span class="account-type-tag">${escapeHtml(txn.transactionType)}</span></td>
            <td style="color: ${isPos ? '#2e7d32' : '#c62828'}; font-weight: 700;">${isPos ? '+' : '−'} ${formatCurrency(Math.abs(txn.amount))}</td>
            <td>${balanceHtml}</td>
            <td>
                <div class="action-buttons">
                    <button onclick="viewTxnDetails('${escapeJs(txn.rowId)}')" class="btn-action-sm btn-view">View</button>
                    <button onclick="openEditTxnModal('${escapeJs(txn.rowId)}')" class="btn-action-sm btn-edit">Edit</button>
                    <button onclick="handleDeleteTxn('${escapeJs(txn.rowId)}')" class="btn-action-sm btn-delete">Delete</button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });

    const resultCountEl = document.getElementById('stmtResultCountText');
    if (resultCountEl) {
        if (isFilterActive) {
            resultCountEl.textContent = `Showing ${filtered.length} of ${cachedStmtData.transactions.length} transactions | Filtered Impact: ${formatCurrency(filteredTotalImpact)}`;
        } else {
            resultCountEl.textContent = `Showing ${filtered.length} of ${cachedStmtData.transactions.length} transactions`;
        }
    }
}

// 4. Transactions View & Advanced Multi-Filter
function onTxnFilterDebounced() {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
        updateFilterUI();
        loadTransactions(1);
    }, 200);
}

function togglePopover(popoverId) {
    const panel = document.getElementById(popoverId);
    if (!panel) return;
    const isOpen = panel.classList.contains('open');
    closeAllPopovers();
    if (!isOpen) {
        panel.classList.add('open');
        const trigger = document.querySelector(`.popover-trigger[data-popover="${popoverId}"]`) || panel.previousElementSibling;
        if (trigger) trigger.classList.add('active');
    }
}

function closeAllPopovers() {
    document.querySelectorAll('.popover-panel').forEach(p => p.classList.remove('open'));
    document.querySelectorAll('.popover-trigger').forEach(t => t.classList.remove('active'));
}

document.addEventListener('click', (e) => {
    const trigger = e.target.closest('.popover-trigger');
    if (trigger) {
        e.preventDefault();
        e.stopPropagation();
        const popoverId = trigger.getAttribute('data-popover') || trigger.id.replace('btn', 'popover').replace('Trigger', '');
        if (popoverId) {
            togglePopover(popoverId);
        }
        return;
    }

    if (!e.target.closest('.popover-panel') && !e.target.closest('.popover-trigger')) {
        closeAllPopovers();
    }
});

async function loadTransactions(page) {
    currentTxnPage = page || 1;
    const searchEl = document.getElementById('txnSearchInput') || document.getElementById('txnSearch');
    const search = searchEl ? searchEl.value.trim() : '';

    const typeStr = selectedTypes.join(',');
    const accountStr = selectedAccounts.join(',');
    const categoryStr = selectedCategories.join(',');
    const subcategoryStr = selectedSubcategories.join(',');
    const payeeStr = selectedPayees.join(',');

    const url = `/api/transactions?page=${currentTxnPage}&pageSize=20&search=${encodeURIComponent(search)}&account=${encodeURIComponent(accountStr)}&category=${encodeURIComponent(categoryStr)}&subcategory=${encodeURIComponent(subcategoryStr)}&payee=${encodeURIComponent(payeeStr)}&transactionType=${encodeURIComponent(typeStr)}&dateFilter=${encodeURIComponent(selectedDatePreset)}&fromDate=${encodeURIComponent(customFromDate)}&toDate=${encodeURIComponent(customToDate)}`;

    try {
        const res = await fetch(url);
        if (!res.ok) throw new Error();
        const data = await res.json();

        const tbody = document.getElementById('transactionsTableBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        data.items.forEach(txn => {
            const tr = document.createElement('tr');
            const isPos = txn.amount >= 0 || txn.transactionType === 'Income';
            tr.innerHTML = `
                <td>${escapeHtml(txn.date)} ${escapeHtml(txn.time)}</td>
                <td>${escapeHtml(txn.payeePayer || txn.description || 'Transaction')}</td>
                <td>${escapeHtml(txn.category || '—')}</td>
                <td>${escapeHtml(txn.subcategory || '—')}</td>
                <td>${escapeHtml(txn.account)}</td>
                <td><span class="account-type-tag">${escapeHtml(txn.transactionType)}</span></td>
                <td style="color: ${isPos ? '#2e7d32' : '#c62828'}; font-weight: 700;">${formatCurrency(txn.amount)}</td>
                <td>
                    <div class="action-buttons">
                        <button onclick="viewTxnDetails('${escapeJs(txn.rowId)}')" class="btn-action-sm btn-view">View</button>
                        <button onclick="openEditTxnModal('${escapeJs(txn.rowId)}')" class="btn-action-sm btn-edit">Edit</button>
                        <button onclick="handleDeleteTxn('${escapeJs(txn.rowId)}')" class="btn-action-sm btn-delete">Delete</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });

        document.getElementById('pageInfo').textContent = `Page ${data.page} of ${data.totalPages || 1} (${data.totalItems} total)`;
        document.getElementById('prevPageBtn').disabled = data.page <= 1;
        document.getElementById('nextPageBtn').disabled = data.page >= data.totalPages;

        const resultCountEl = document.getElementById('resultCountText');
        if (resultCountEl) {
            resultCountEl.textContent = `Showing ${data.totalItems} transactions`;
        }
    } catch (e) {
        console.error('Failed to load transactions', e);
    }
}

function changeTxnPage(dir) {
    loadTransactions(currentTxnPage + dir);
}

// Populate Multi-Filter Lists
async function populateFilters() {
    try {
        const accRes = await fetch('/api/accounts');
        if (accRes.ok) {
            cachedAccounts = await accRes.json();
            renderAccountPopoverItems();
        }

        const catRes = await fetch('/api/categories');
        if (catRes.ok) {
            cachedCategories = await catRes.json();
            renderCategoryPopoverItems();
            renderSubcategoryPopoverItems();
        }

        const payeeRes = await fetch('/api/payees');
        if (payeeRes.ok) {
            cachedPayees = await payeeRes.json();
            renderPayeePopoverItems();
        }
    } catch (e) {
        console.error('Failed to populate filters', e);
    }
    updateFilterUI();
}

function renderAccountPopoverItems() {
    const container = document.getElementById('listAccountItems');
    if (!container) return;
    container.innerHTML = '';

    cachedAccounts.forEach(acc => {
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = selectedAccounts.includes(acc.nickName);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(acc.nickName)}" ${isChecked ? 'checked' : ''} onchange="onAccountFilterCheckboxChanged(this)">
            <span>${escapeHtml(acc.nickName)}</span>
        `;
        container.appendChild(label);
    });
}

function onAccountFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!selectedAccounts.includes(cb.value)) selectedAccounts.push(cb.value);
    } else {
        selectedAccounts = selectedAccounts.filter(v => v !== cb.value);
    }
    updateFilterUI();
    loadTransactions(1);
}

function renderCategoryPopoverItems() {
    const container = document.getElementById('listCategoryItems');
    if (!container) return;
    container.innerHTML = '';

    Object.keys(cachedCategories).sort().forEach(cat => {
        if (!cat) return;
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = selectedCategories.includes(cat);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(cat)}" ${isChecked ? 'checked' : ''} onchange="onCategoryFilterCheckboxChanged(this)">
            <span>${escapeHtml(cat)}</span>
        `;
        container.appendChild(label);
    });
}

function onCategoryFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!selectedCategories.includes(cb.value)) selectedCategories.push(cb.value);
    } else {
        selectedCategories = selectedCategories.filter(v => v !== cb.value);
    }
    renderSubcategoryPopoverItems();
    updateFilterUI();
    loadTransactions(1);
}

function renderSubcategoryPopoverItems() {
    const container = document.getElementById('listSubcategoryItems');
    if (!container) return;
    container.innerHTML = '';

    let availableSubs = [];
    if (selectedCategories.length === 0) {
        Object.values(cachedCategories).forEach(list => {
            availableSubs = availableSubs.concat(list);
        });
    } else {
        selectedCategories.forEach(cat => {
            if (cachedCategories[cat]) {
                availableSubs = availableSubs.concat(cachedCategories[cat]);
            }
        });
    }
    availableSubs = Array.from(new Set(availableSubs)).sort();

    const noSubLabel = document.createElement('label');
    noSubLabel.className = 'popover-option';
    const isNoSubChecked = selectedSubcategories.includes('__NO_SUBCATEGORY__');
    noSubLabel.innerHTML = `
        <input type="checkbox" value="__NO_SUBCATEGORY__" ${isNoSubChecked ? 'checked' : ''} onchange="onSubcategoryFilterCheckboxChanged(this)">
        <span style="font-style: italic; color: #64748b;">[No Subcategory]</span>
    `;
    container.appendChild(noSubLabel);

    availableSubs.forEach(sub => {
        if (!sub) return;
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = selectedSubcategories.includes(sub);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(sub)}" ${isChecked ? 'checked' : ''} onchange="onSubcategoryFilterCheckboxChanged(this)">
            <span>${escapeHtml(sub)}</span>
        `;
        container.appendChild(label);
    });

    selectedSubcategories = selectedSubcategories.filter(s => s === '__NO_SUBCATEGORY__' || availableSubs.includes(s));
}

function onSubcategoryFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!selectedSubcategories.includes(cb.value)) selectedSubcategories.push(cb.value);
    } else {
        selectedSubcategories = selectedSubcategories.filter(v => v !== cb.value);
    }
    updateFilterUI();
    loadTransactions(1);
}

function renderPayeePopoverItems() {
    const container = document.getElementById('listPayeeItems');
    if (!container) return;
    container.innerHTML = '';

    cachedPayees.forEach(payee => {
        if (!payee) return;
        const label = document.createElement('label');
        label.className = 'popover-option';
        const isChecked = selectedPayees.includes(payee);
        label.innerHTML = `
            <input type="checkbox" value="${escapeHtml(payee)}" ${isChecked ? 'checked' : ''} onchange="onPayeeFilterCheckboxChanged(this)">
            <span>${escapeHtml(payee)}</span>
        `;
        container.appendChild(label);
    });
}

function onPayeeFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!selectedPayees.includes(cb.value)) selectedPayees.push(cb.value);
    } else {
        selectedPayees = selectedPayees.filter(v => v !== cb.value);
    }
    updateFilterUI();
    loadTransactions(1);
}

function onTypeFilterCheckboxChanged(cb) {
    if (cb.checked) {
        if (!selectedTypes.includes(cb.value)) selectedTypes.push(cb.value);
    } else {
        selectedTypes = selectedTypes.filter(v => v !== cb.value);
    }
    updateFilterUI();
    loadTransactions(1);
}

function selectAllPopoverItems(filterType, event) {
    if (event) { event.preventDefault(); event.stopPropagation(); }
    if (filterType === 'Type') {
        selectedTypes = ['Expense', 'Income', 'Transfer'];
        document.querySelectorAll('#listTypeItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    } else if (filterType === 'Account') {
        selectedAccounts = cachedAccounts.map(a => a.nickName);
        document.querySelectorAll('#listAccountItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    } else if (filterType === 'Category') {
        selectedCategories = Object.keys(cachedCategories).filter(Boolean);
        document.querySelectorAll('#listCategoryItems input[type="checkbox"]').forEach(cb => cb.checked = true);
        renderSubcategoryPopoverItems();
    } else if (filterType === 'Subcategory') {
        let availableSubs = ['__NO_SUBCATEGORY__'];
        if (selectedCategories.length === 0) {
            Object.values(cachedCategories).forEach(list => availableSubs = availableSubs.concat(list));
        } else {
            selectedCategories.forEach(cat => { if (cachedCategories[cat]) availableSubs = availableSubs.concat(cachedCategories[cat]); });
        }
        selectedSubcategories = Array.from(new Set(availableSubs)).filter(Boolean);
        document.querySelectorAll('#listSubcategoryItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    } else if (filterType === 'Payee') {
        selectedPayees = cachedPayees.filter(Boolean);
        document.querySelectorAll('#listPayeeItems input[type="checkbox"]').forEach(cb => cb.checked = true);
    }
    updateFilterUI();
    loadTransactions(1);
}

function clearPopoverItems(filterType, event) {
    if (event) { event.preventDefault(); event.stopPropagation(); }
    if (filterType === 'Type') {
        selectedTypes = [];
        document.querySelectorAll('#listTypeItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    } else if (filterType === 'Account') {
        selectedAccounts = [];
        document.querySelectorAll('#listAccountItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    } else if (filterType === 'Category') {
        selectedCategories = [];
        document.querySelectorAll('#listCategoryItems input[type="checkbox"]').forEach(cb => cb.checked = false);
        renderSubcategoryPopoverItems();
    } else if (filterType === 'Subcategory') {
        selectedSubcategories = [];
        document.querySelectorAll('#listSubcategoryItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    } else if (filterType === 'Payee') {
        selectedPayees = [];
        document.querySelectorAll('#listPayeeItems input[type="checkbox"]').forEach(cb => cb.checked = false);
    }
    updateFilterUI();
    loadTransactions(1);
}

function filterPopoverList(filterType, searchVal) {
    const query = searchVal.trim().toLowerCase();
    const containerId = `list${filterType}Items`;
    const container = document.getElementById(containerId);
    if (!container) return;

    container.querySelectorAll('.popover-option').forEach(label => {
        const text = label.textContent.toLowerCase();
        label.style.display = text.includes(query) ? 'flex' : 'none';
    });
}

function updateFilterUI() {
    updateDateTriggerText('btnDateTrigger', selectedDatePreset);
    updateTriggerText('btnTypeTrigger', selectedTypes, 'Type');
    updateTriggerText('btnAccountTrigger', selectedAccounts, 'Account');
    updateTriggerText('btnCategoryTrigger', selectedCategories, 'Category');

    const subDisplayNames = selectedSubcategories.map(s => (s === '__NO_SUBCATEGORY__' || s === 'No Subcategory' || s === '[No Subcategory]') ? 'No Subcategory' : s);
    updateTriggerText('btnSubcategoryTrigger', subDisplayNames, 'Subcategory');

    updateTriggerText('btnPayeeTrigger', selectedPayees, 'Payee / Payer');

    const searchEl = document.getElementById('txnSearchInput') || document.getElementById('txnSearch');
    const search = searchEl ? searchEl.value.trim() : '';
    const hasActiveFilters = selectedDatePreset !== 'ALL' ||
        selectedTypes.length > 0 ||
        selectedAccounts.length > 0 ||
        selectedCategories.length > 0 ||
        selectedSubcategories.length > 0 ||
        selectedPayees.length > 0 ||
        search !== '';

    const clearBtn = document.getElementById('btnClearAllFilters');
    if (clearBtn) {
        clearBtn.style.display = hasActiveFilters ? 'inline-flex' : 'none';
    }
}

function updateTriggerText(triggerId, selectedArray, defaultLabel) {
    const trigger = document.getElementById(triggerId);
    if (!trigger) return;

    let text = `${defaultLabel}: All`;
    if (selectedArray && selectedArray.length === 1) {
        text = `${defaultLabel}: ${selectedArray[0]}`;
    } else if (selectedArray && selectedArray.length > 1) {
        text = `${defaultLabel}: ${selectedArray.length} selected`;
    }

    const firstSpan = trigger.querySelector('span:not(.arrow)');
    if (firstSpan) {
        firstSpan.textContent = text;
    } else {
        trigger.innerHTML = `<span>${escapeHtml(text)}</span> <span class="arrow">▾</span>`;
    }

    if (selectedArray && selectedArray.length > 0) {
        trigger.classList.add('has-selections', 'has-selection');
    } else {
        trigger.classList.remove('has-selections', 'has-selection');
    }
}

function clearAllFilters() {
    selectedDatePreset = 'ALL';
    customFromDate = '';
    customToDate = '';
    selectedTypes = [];
    selectedAccounts = [];
    selectedCategories = [];
    selectedSubcategories = [];
    selectedPayees = [];

    const searchInput = document.getElementById('txnSearchInput') || document.getElementById('txnSearch');
    if (searchInput) searchInput.value = '';

    const allRadio = document.querySelector('input[name="dateOpt"][value="ALL"]');
    if (allRadio) allRadio.checked = true;

    const fromInput = document.getElementById('inputFromDate');
    if (fromInput) fromInput.value = '';
    const toInput = document.getElementById('inputToDate');
    if (toInput) toInput.value = '';

    const box = document.getElementById('boxCustomDate');
    if (box) box.style.display = 'none';
    const err = document.getElementById('errCustomDate');
    if (err) { err.style.display = 'none'; err.textContent = ''; }

    document.querySelectorAll('#viewTransactions .popover-panel input[type="checkbox"]').forEach(cb => cb.checked = false);

    document.querySelectorAll('#viewTransactions .popover-panel input[type="checkbox"]').forEach(cb => cb.checked = false);

    renderSubcategoryPopoverItems();
    updateFilterUI();
    loadTransactions(1);
}

function clearAllTxnFilters() {
    clearAllFilters();
}

// 5. Categories View
async function loadCategories() {
    try {
        const res = await fetch('/api/categories');
        if (!res.ok) throw new Error();
        cachedCategories = await res.json();

        const grid = document.getElementById('categoriesGrid');
        grid.innerHTML = '';

        Object.keys(cachedCategories).sort().forEach(cat => {
            if (!cat) return;
            const subs = cachedCategories[cat];
            const card = document.createElement('div');
            card.className = 'category-card';

            let subsHtml = subs.map(s => `<li>${escapeHtml(s)}</li>`).join('');
            if (!subsHtml) subsHtml = '<li style="color:#94a3b8; font-style:italic;">No subcategories</li>';

            card.innerHTML = `
                <h3>${escapeHtml(cat)}</h3>
                <ul class="subcat-list">${subsHtml}</ul>
            `;
            grid.appendChild(card);
        });
    } catch (e) {
        console.error('Failed to load categories', e);
    }
}

// Phase 2A: Transaction Modals & Handlers
function openAddTxnModal() {
    document.getElementById('modalTitle').textContent = 'Add Transaction';
    document.getElementById('formRowId').value = '';
    document.getElementById('txnForm').reset();
    document.getElementById('formError').textContent = '';
    hidePayeeSuggestions();

    const radios = document.getElementsByName('formType');
    radios.forEach(r => r.checked = (r.value === 'Expense'));

    const now = new Date();
    const day = String(now.getDate()).padStart(2, '0');
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const year = now.getFullYear();
    const hours = String(now.getHours()).padStart(2, '0');
    const mins = String(now.getMinutes()).padStart(2, '0');

    document.getElementById('formDate').value = `${day}-${month}-${year}`;
    document.getElementById('formTime').value = `${hours}:${mins}`;

    onFormTypeChange();
    populateFormDropdowns();
    document.getElementById('txnModal').style.display = 'flex';
}

async function openEditTxnModal(rowId) {
    try {
        if (!cachedAccounts || cachedAccounts.length === 0) {
            await populateFilters();
        }
        const res = await fetch(`/api/transactions/${encodeURIComponent(rowId)}`);
        if (!res.ok) throw new Error('Transaction not found');
        const txn = await res.json();

        document.getElementById('modalTitle').textContent = 'Edit Transaction';
        document.getElementById('formRowId').value = txn.rowId;
        document.getElementById('formError').textContent = '';
        hidePayeeSuggestions();

        const radios = document.getElementsByName('formType');
        radios.forEach(r => r.checked = (r.value === txn.transactionType));

        onFormTypeChange();

        document.getElementById('formAmount').value = Math.abs(txn.amount);
        document.getElementById('formDate').value = txn.date;
        document.getElementById('formTime').value = txn.time || '12:00';
        document.getElementById('formCategory').value = txn.category || '';
        document.getElementById('formSubcategory').value = txn.subcategory || '';
        document.getElementById('formPaymentMethod').value = txn.paymentMethod || '';
        document.getElementById('formPayeePayer').value = txn.payeePayer || '';
        document.getElementById('formDescription').value = txn.description || '';
        document.getElementById('formRefCheckNo').value = txn.refCheckNo || '';
        document.getElementById('formTag').value = txn.tag || '';

        populateFormDropdowns(txn.account, txn.toAccount);
        document.getElementById('txnModal').style.display = 'flex';
    } catch (e) {
        alert('Failed to load transaction for editing: ' + e.message);
    }
}

function populateFormDropdowns(selectedAccountName, selectedToAccountName) {
    const accSelect = document.getElementById('formAccount');
    const toAccSelect = document.getElementById('formToAccount');
    accSelect.innerHTML = '';
    toAccSelect.innerHTML = '';

    let accountFound = false;
    let toAccountFound = false;

    cachedAccounts.forEach(acc => {
        const opt1 = document.createElement('option');
        opt1.value = acc.nickName;
        opt1.textContent = acc.nickName;
        if (selectedAccountName && acc.nickName === selectedAccountName) {
            opt1.selected = true;
            accountFound = true;
        }
        accSelect.appendChild(opt1);

        const opt2 = document.createElement('option');
        opt2.value = acc.nickName;
        opt2.textContent = acc.nickName;
        if (selectedToAccountName && acc.nickName === selectedToAccountName) {
            opt2.selected = true;
            toAccountFound = true;
        }
        toAccSelect.appendChild(opt2);
    });

    if (selectedAccountName && !accountFound) {
        const opt = document.createElement('option');
        opt.value = selectedAccountName;
        opt.textContent = selectedAccountName;
        opt.selected = true;
        accSelect.appendChild(opt);
    }

    if (selectedToAccountName && !toAccountFound) {
        const opt = document.createElement('option');
        opt.value = selectedToAccountName;
        opt.textContent = selectedToAccountName;
        opt.selected = true;
        toAccSelect.appendChild(opt);
    }

    const catDatalist = document.getElementById('categoryDatalist');
    catDatalist.innerHTML = '';
    Object.keys(cachedCategories).sort().forEach(cat => {
        if (cat) {
            const opt = document.createElement('option');
            opt.value = cat;
            catDatalist.appendChild(opt);
        }
    });
}

function closeTxnModal() {
    document.getElementById('txnModal').style.display = 'none';
}

function closeViewModal() {
    document.getElementById('viewModal').style.display = 'none';
}

function onFormTypeChange() {
    const typeRadios = document.getElementsByName('formType');
    let txnType = 'Expense';
    typeRadios.forEach(r => { if (r.checked) txnType = r.value; });

    const toAccGroup = document.getElementById('formToAccountGroup');
    const lblAccount = document.getElementById('lblFormAccount');

    if (txnType === 'Transfer') {
        toAccGroup.style.display = 'block';
        lblAccount.textContent = 'From Account *';
    } else {
        toAccGroup.style.display = 'none';
        lblAccount.textContent = 'Account *';
    }
}

// Autocomplete logic for Payee/Payer
function onPayeeInputChanged() {
    clearTimeout(payeeDebounceTimer);
    const query = document.getElementById('formPayeePayer').value.trim();

    if (query.length < 1) {
        hidePayeeSuggestions();
        return;
    }

    payeeDebounceTimer = setTimeout(async () => {
        try {
            const res = await fetch(`/api/autocomplete/payees?q=${encodeURIComponent(query)}`);
            if (!res.ok) return;
            const data = await res.json();
            currentSuggestions = data.suggestions || [];
            renderPayeeSuggestions(currentSuggestions);
        } catch (e) {
            hidePayeeSuggestions();
        }
    }, 200);
}

function renderPayeeSuggestions(suggestions) {
    const dropdown = document.getElementById('payeeSuggestions');
    dropdown.innerHTML = '';
    selectedSuggestionIndex = -1;

    if (!suggestions || suggestions.length === 0) {
        dropdown.style.display = 'none';
        return;
    }

    suggestions.forEach((item, index) => {
        const div = document.createElement('div');
        div.className = 'suggestion-item';
        div.dataset.index = index;

        const catText = item.category ? `${item.category}${item.subcategory ? ' : ' + item.subcategory : ''}` : '';

        div.innerHTML = `
            <span class="suggestion-title">${escapeHtml(item.payeePayer)}</span>
            <span class="suggestion-meta">${escapeHtml(catText)}</span>
        `;

        div.addEventListener('mousedown', (e) => {
            e.preventDefault();
            selectPayeeSuggestion(item);
        });

        dropdown.appendChild(div);
    });

    dropdown.style.display = 'block';
}

function selectPayeeSuggestion(item) {
    document.getElementById('formPayeePayer').value = item.payeePayer;

    if (item.category && item.category !== 'Imported') {
        document.getElementById('formCategory').value = item.category;
    }

    if (item.subcategory) {
        document.getElementById('formSubcategory').value = item.subcategory;
    }

    hidePayeeSuggestions();
}

function hidePayeeSuggestions() {
    const dropdown = document.getElementById('payeeSuggestions');
    if (dropdown) {
        dropdown.style.display = 'none';
        dropdown.innerHTML = '';
    }
    currentSuggestions = [];
    selectedSuggestionIndex = -1;
}

function handlePayeeKeydown(e) {
    const dropdown = document.getElementById('payeeSuggestions');
    if (dropdown.style.display === 'none' || currentSuggestions.length === 0) return;

    const items = dropdown.querySelectorAll('.suggestion-item');

    if (e.key === 'ArrowDown') {
        e.preventDefault();
        selectedSuggestionIndex = (selectedSuggestionIndex + 1) % currentSuggestions.length;
        updateActiveSuggestion(items);
    } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        selectedSuggestionIndex = (selectedSuggestionIndex - 1 + currentSuggestions.length) % currentSuggestions.length;
        updateActiveSuggestion(items);
    } else if (e.key === 'Enter') {
        if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < currentSuggestions.length) {
            e.preventDefault();
            selectPayeeSuggestion(currentSuggestions[selectedSuggestionIndex]);
        }
    } else if (e.key === 'Escape') {
        hidePayeeSuggestions();
    }
}

function updateActiveSuggestion(items) {
    items.forEach((el, idx) => {
        if (idx === selectedSuggestionIndex) {
            el.classList.add('active');
            el.scrollIntoView({ block: 'nearest' });
        } else {
            el.classList.remove('active');
        }
    });
}

document.addEventListener('click', (e) => {
    const container = document.querySelector('.autocomplete-container');
    if (container && !container.contains(e.target)) {
        hidePayeeSuggestions();
    }
});

async function handleSaveTxn(e) {
    e.preventDefault();
    const saveBtn = document.getElementById('saveTxnBtn');
    const errorEl = document.getElementById('formError');
    errorEl.textContent = '';
    saveBtn.disabled = true;

    const rowId = document.getElementById('formRowId').value;
    const isEdit = Boolean(rowId);

    const typeRadios = document.getElementsByName('formType');
    let txnType = 'Expense';
    typeRadios.forEach(r => { if (r.checked) txnType = r.value; });

    const payload = {
        transactionType: txnType,
        amount: parseFloat(document.getElementById('formAmount').value),
        date: document.getElementById('formDate').value.trim(),
        time: document.getElementById('formTime').value.trim() || '12:00',
        account: document.getElementById('formAccount').value,
        toAccount: txnType === 'Transfer' ? document.getElementById('formToAccount').value : null,
        category: document.getElementById('formCategory').value.trim() || (txnType === 'Transfer' ? 'Transfer' : 'Uncategorized'),
        subcategory: document.getElementById('formSubcategory').value.trim(),
        paymentMethod: document.getElementById('formPaymentMethod').value.trim(),
        payeePayer: document.getElementById('formPayeePayer').value.trim(),
        description: document.getElementById('formDescription').value.trim(),
        refCheckNo: document.getElementById('formRefCheckNo').value.trim(),
        tag: document.getElementById('formTag').value.trim()
    };

    const url = isEdit ? `/api/transactions/${encodeURIComponent(rowId)}` : '/api/transactions';
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const res = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        let data = {};
        try {
            data = await res.json();
        } catch (jsonErr) {}

        if (res.ok) {
            closeTxnModal();
            refreshAllActiveViews();
        } else {
            errorEl.textContent = data.error || `Server error (${res.status})`;
        }
    } catch (err) {
        errorEl.textContent = 'Server connection error.';
    } finally {
        saveBtn.disabled = false;
    }
}

async function handleDeleteTxn(rowId) {
    if (!confirm('Are you sure you want to delete this transaction?')) return;

    try {
        const res = await fetch(`/api/transactions/${encodeURIComponent(rowId)}`, {
            method: 'DELETE'
        });

        if (res.ok) {
            refreshAllActiveViews();
        } else {
            const data = await res.json();
            alert('Failed to delete: ' + (data.error || 'Unknown error'));
        }
    } catch (e) {
        alert('Failed to connect to server.');
    }
}

async function viewTxnDetails(rowId) {
    try {
        const res = await fetch(`/api/transactions/${encodeURIComponent(rowId)}`);
        if (!res.ok) throw new Error('Transaction not found');
        const txn = await res.json();

        const container = document.getElementById('viewDetailsContent');
        container.innerHTML = `
            <div class="details-row"><span>Row ID</span><strong>${escapeHtml(txn.rowId)}</strong></div>
            <div class="details-row"><span>Type</span><strong>${escapeHtml(txn.transactionType)}</strong></div>
            <div class="details-row"><span>Amount</span><strong style="color: ${txn.amount >= 0 ? '#2e7d32' : '#c62828'}">${formatCurrency(txn.amount)}</strong></div>
            <div class="details-row"><span>Date & Time</span><strong>${escapeHtml(txn.date)} ${escapeHtml(txn.time)}</strong></div>
            <div class="details-row"><span>Account</span><strong>${escapeHtml(txn.account)}</strong></div>
            <div class="details-row"><span>To Account</span><strong>${escapeHtml(txn.toAccount || 'N/A')}</strong></div>
            <div class="details-row"><span>Category</span><strong>${escapeHtml(txn.category)}</strong></div>
            <div class="details-row"><span>Subcategory</span><strong>${escapeHtml(txn.subcategory || 'N/A')}</strong></div>
            <div class="details-row"><span>Payee / Payer</span><strong>${escapeHtml(txn.payeePayer || 'N/A')}</strong></div>
            <div class="details-row"><span>Payment Method</span><strong>${escapeHtml(txn.paymentMethod || 'N/A')}</strong></div>
            <div class="details-row"><span>Description</span><strong>${escapeHtml(txn.description || 'N/A')}</strong></div>
            <div class="details-row"><span>Reference No</span><strong>${escapeHtml(txn.refCheckNo || 'N/A')}</strong></div>
            <div class="details-row"><span>Tag</span><strong>${escapeHtml(txn.tag || 'N/A')}</strong></div>
        `;
        document.getElementById('viewModal').style.display = 'flex';
    } catch (e) {
        alert('Failed to load transaction details: ' + e.message);
    }
}

function refreshAllActiveViews() {
    loadDashboard();
    loadAccounts();
    loadTransactions(currentTxnPage);
    populateFilters();

    const stmtSection = document.getElementById('viewStatement');
    if (stmtSection.classList.contains('active')) {
        const name = document.getElementById('stmtAccountName').textContent;
        if (name) viewStatement(name);
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function escapeJs(str) {
    if (!str) return '';
    return String(str)
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'")
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r');
}

function formatCurrency(amount) {
    const val = Math.abs(amount || 0);
    return '₹' + val.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
