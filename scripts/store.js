"use strict";

const STORAGE = { cart: "thinkmart-cart" };
const FREE_SHIPPING_MINIMUM = 5000;
const SHIPPING_FEE = 99;
const INR = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
let PRODUCTS = [];
let currentUser = null;

function readStorage(key, fallback) {
    const stored = localStorage.getItem(key);
    return stored === null ? fallback : JSON.parse(stored);
}

function writeStorage(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
}

function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        credentials: "same-origin",
        headers: {
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...options.headers
        }
    });
    const payload = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(payload?.message || `The request failed (${response.status}).`);
        error.status = response.status;
        throw error;
    }
    return payload;
}

function cartContents() {
    const cart = readStorage(STORAGE.cart, {});
    return cart && typeof cart === "object" && !Array.isArray(cart) ? cart : {};
}

function cartCount() {
    return Object.values(cartContents()).reduce((sum, quantity) =>
        sum + (Number.isInteger(quantity) && quantity > 0 ? quantity : 0), 0);
}

function showMessage(message, isError = false) {
    const status = document.querySelector("#live-status");
    if (!status) return;
    status.textContent = message;
    status.classList.toggle("is-error", isError);
    status.classList.add("is-visible");
    clearTimeout(showMessage.timer);
    showMessage.timer = setTimeout(() => status.classList.remove("is-visible"), 3200);
}

function renderChrome() {
    const header = document.querySelector("#site-header");
    const footer = document.querySelector("#site-footer");
    const page = document.body.dataset.page;
    const accountLabel = currentUser ? "Account" : "Sign in";
    const itemCount = cartCount();

    if (header) {
        header.innerHTML = `
            <header class="site-header">
                <a class="brand" href="landingpage.html" aria-label="ThinkMart home"><span class="brand-mark">t</span>ThinkMart</a>
                <nav class="site-nav" aria-label="Main navigation">
                    <a href="landingpage.html" ${page === "home" ? 'aria-current="page"' : ""}>Home</a>
                    <a href="products.html" ${page === "products" ? 'aria-current="page"' : ""}>Shop</a>
                    <a href="About.html" ${page === "about" ? 'aria-current="page"' : ""}>Our story</a>
                    <a href="Contact.html" ${page === "contact" ? 'aria-current="page"' : ""}>Contact</a>
                    <a href="${currentUser ? "account.html" : "login.html"}">${accountLabel}</a>
                    <a class="nav-cart" href="cart.html" ${page === "cart" ? 'aria-current="page"' : ""}>Cart <span class="cart-count" aria-label="${itemCount} items">${itemCount}</span></a>
                </nav>
            </header>`;
    }

    if (footer) {
        footer.innerHTML = `
            <footer class="site-footer">
                <div class="container footer-content">
                    <div><a class="brand footer-brand" href="landingpage.html"><span class="brand-mark">t</span>ThinkMart</a><p>Thoughtful everyday finds, made easy to discover and a pleasure to bring home.</p></div>
                    <nav class="footer-links" aria-label="Footer navigation">
                        <a href="products.html">Shop all</a><a href="About.html">Our story</a><a href="Contact.html">Contact us</a><a href="account.html">Your orders</a>
                    </nav>
                    <p class="copyright">© ${new Date().getFullYear()} ThinkMart. Orders are saved securely by the Java server.</p>
                </div>
            </footer>`;
    }
}

function productCard(product) {
    return `
        <article class="product-card">
            <div class="product-image-wrap">
                <img class="product-image" src="${escapeHTML(product.image)}" alt="${escapeHTML(product.name)}" loading="lazy">
                <span class="product-tag">${escapeHTML(product.tag)}</span>
            </div>
            <div class="product-info">
                <p class="product-category">${escapeHTML(product.category)}</p>
                <h3>${escapeHTML(product.name)}</h3>
                <p class="product-description">${escapeHTML(product.description)}</p>
                <div class="product-meta"><span class="product-price">${INR.format(product.price)}</span><span class="product-rating" aria-label="${product.rating} out of 5 stars">★ ${product.rating}</span></div>
                <div class="product-actions">
                    <button class="button button-small" type="button" data-add-to-cart="${escapeHTML(product.id)}">Add to cart</button>
                    <a class="button button-outline button-small" href="cart.html" data-buy-now="${escapeHTML(product.id)}">Buy now</a>
                </div>
            </div>
        </article>`;
}

function renderProducts(container, products) {
    container.innerHTML = products.map(productCard).join("");
}

function showLoadError(container, message) {
    container.innerHTML = `<div class="empty-state" style="grid-column:1/-1"><h2>We couldn't load the shop</h2><p>${escapeHTML(message)}</p><p>Check that the ThinkMart Java server is running, then refresh this page.</p></div>`;
}

async function loadProducts(container) {
    try {
        PRODUCTS = await api("/api/products");
        return true;
    } catch (error) {
        showLoadError(container, error.message);
        return false;
    }
}

function addToCart(productId, goToCart = false) {
    const product = PRODUCTS.find((item) => item.id === productId);
    if (!product) {
        showMessage("That product is no longer available.", true);
        return;
    }
    const cart = cartContents();
    const quantity = Number.isInteger(cart[productId]) ? cart[productId] : 0;
    if (quantity >= 99) {
        showMessage("You can add up to 99 of each item.", true);
        return;
    }
    cart[productId] = quantity + 1;
    writeStorage(STORAGE.cart, cart);
    renderChrome();
    if (goToCart) {
        window.location.href = "cart.html";
        return;
    }
    showMessage(`${product.name} added to your cart.`);
}

async function initializeHome() {
    const grid = document.querySelector("#featured-products");
    if (grid && await loadProducts(grid)) renderProducts(grid, PRODUCTS.slice(0, 4));
}

async function initializeShop() {
    const grid = document.querySelector("#product-grid");
    if (!grid || !await loadProducts(grid)) return;
    const search = document.querySelector("#product-search");
    const category = document.querySelector("#category-filter");
    const sort = document.querySelector("#sort-products");
    const count = document.querySelector("#result-count");

    function update() {
        const query = search.value.trim().toLowerCase();
        let visible = PRODUCTS.filter((product) =>
            (category.value === "all" || product.category === category.value) &&
            `${product.name} ${product.description} ${product.category}`.toLowerCase().includes(query)
        );
        if (sort.value === "price-low") visible = visible.sort((a, b) => a.price - b.price);
        if (sort.value === "price-high") visible = visible.sort((a, b) => b.price - a.price);
        if (sort.value === "rating") visible = visible.sort((a, b) => b.rating - a.rating);
        count.textContent = `${visible.length} ${visible.length === 1 ? "item" : "items"}${query ? ` matching “${search.value.trim()}”` : ""}`;
        if (visible.length) {
            renderProducts(grid, visible);
        } else {
            grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1"><h2>No matches yet</h2><p>Try another search or choose a different category.</p></div>`;
        }
    }
    search.addEventListener("input", update);
    category.addEventListener("change", update);
    sort.addEventListener("change", update);
    update();
}

function cartTotals(cart) {
    const subtotal = Object.entries(cart).reduce((sum, [id, quantity]) => {
        const product = PRODUCTS.find((item) => item.id === id);
        return product && Number.isInteger(quantity) && quantity > 0
            ? sum + product.price * quantity
            : sum;
    }, 0);
    const shipping = subtotal === 0 || subtotal >= FREE_SHIPPING_MINIMUM ? 0 : SHIPPING_FEE;
    return { subtotal, shipping, total: subtotal + shipping };
}

async function refreshCurrentUser() {
    try {
        currentUser = await api("/api/auth/me");
    } catch (error) {
        if (error.status !== 401) throw error;
        currentUser = null;
    }
    renderChrome();
}

async function renderCart() {
    const itemsElement = document.querySelector("#cart-items");
    if (!itemsElement) return;
    if (!await loadProducts(itemsElement)) {
        document.querySelector("#cart-summary").hidden = true;
        document.querySelector("#checkout-section").hidden = true;
        return;
    }
    const cart = cartContents();
    const validEntries = Object.entries(cart).filter(([id, quantity]) =>
        PRODUCTS.some((product) => product.id === id) &&
        Number.isInteger(quantity) && quantity > 0 && quantity <= 99
    );
    const normalizedCart = Object.fromEntries(validEntries);
    if (JSON.stringify(normalizedCart) !== JSON.stringify(cart)) writeStorage(STORAGE.cart, normalizedCart);
    const products = validEntries.map(([id, quantity]) => ({ product: PRODUCTS.find((item) => item.id === id), quantity }));

    if (products.length === 0) {
        itemsElement.innerHTML = `<div class="empty-state"><h2>Your cart is waiting</h2><p>Find something you'll love and it will show up here.</p><a class="button" href="products.html">Explore the shop</a></div>`;
        document.querySelector("#cart-summary").hidden = true;
        document.querySelector("#checkout-section").hidden = true;
        renderChrome();
        return;
    }

    itemsElement.innerHTML = products.map(({ product, quantity }) => `
        <article class="cart-item">
            <img src="${escapeHTML(product.image)}" alt="${escapeHTML(product.name)}">
            <div><h3>${escapeHTML(product.name)}</h3><p>${escapeHTML(product.category)}</p><p class="cart-item-price">${INR.format(product.price * quantity)}</p></div>
            <div class="cart-item-controls">
                <div class="quantity-control" aria-label="Quantity for ${escapeHTML(product.name)}">
                    <button type="button" data-quantity="${product.id}" data-change="-1" aria-label="Decrease quantity">−</button>
                    <output>${quantity}</output>
                    <button type="button" data-quantity="${product.id}" data-change="1" aria-label="Increase quantity">+</button>
                </div>
                <button class="button button-danger button-small" type="button" data-remove="${product.id}">Remove</button>
            </div>
        </article>`).join("");

    const totals = cartTotals(normalizedCart);
    document.querySelector("#cart-summary").hidden = false;
    document.querySelector("#checkout-section").hidden = false;
    document.querySelector("#subtotal").textContent = INR.format(totals.subtotal);
    document.querySelector("#shipping").textContent = totals.shipping === 0 ? "Free" : INR.format(totals.shipping);
    document.querySelector("#total").textContent = INR.format(totals.total);
    document.querySelector("#shipping-note").textContent = totals.subtotal >= FREE_SHIPPING_MINIMUM
        ? "Your order qualifies for free delivery."
        : `Add ${INR.format(FREE_SHIPPING_MINIMUM - totals.subtotal)} more for free delivery.`;
    if (currentUser) {
        document.querySelector("#customer-name").value = currentUser.name || "";
        document.querySelector("#customer-email").value = currentUser.email || "";
    }
    renderChrome();
}

function initializeCart() {
    const itemsElement = document.querySelector("#cart-items");
    if (!itemsElement) return;
    itemsElement.addEventListener("click", (event) => {
        const target = event.target.closest("button");
        if (!target) return;
        const cart = cartContents();
        if (target.dataset.quantity) {
            const next = (cart[target.dataset.quantity] || 0) + Number(target.dataset.change);
            if (next < 1) delete cart[target.dataset.quantity];
            else if (next <= 99) cart[target.dataset.quantity] = next;
            writeStorage(STORAGE.cart, cart);
            renderCart();
        } else if (target.dataset.remove) {
            delete cart[target.dataset.remove];
            writeStorage(STORAGE.cart, cart);
            renderCart();
            showMessage("Item removed from your cart.");
        }
    });

    const form = document.querySelector("#checkout-form");
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const cart = cartContents();
        const items = Object.entries(cart)
            .filter(([id, quantity]) => PRODUCTS.some((product) => product.id === id) &&
                Number.isInteger(quantity) && quantity > 0 && quantity <= 99)
            .map(([id, quantity]) => ({ id, quantity }));
        if (items.length === 0) {
            showMessage("Add an item before placing your order.", true);
            renderCart();
            return;
        }
        const data = new FormData(form);
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        button.textContent = "Placing order…";
        try {
            const order = await api("/api/orders", {
                method: "POST",
                body: JSON.stringify({
                    name: data.get("name"),
                    email: data.get("email"),
                    phone: data.get("phone"),
                    address: data.get("address"),
                    city: data.get("city"),
                    postalCode: data.get("postalCode"),
                    items
                })
            });
            writeStorage(STORAGE.cart, {});
            document.querySelector("#checkout-section").hidden = true;
            document.querySelector("#cart-summary").hidden = true;
            itemsElement.innerHTML = `<section class="order-confirmation" role="status"><p class="eyebrow">Order confirmed</p><h2>Thank you, ${escapeHTML(order.name)}!</h2><p>Your order <strong>${escapeHTML(order.id)}</strong> has been saved by the ThinkMart server. No payment was collected. Your order total is <strong>${INR.format(order.total)}</strong>.</p><a class="button" href="products.html">Continue shopping</a> <a class="button button-outline" href="account.html">View your orders</a></section>`;
            renderChrome();
        } catch (error) {
            showMessage(error.message || "We couldn't place your order. Please try again.", true);
            button.disabled = false;
            button.textContent = "Place demo order";
        }
    });
    renderCart();
}

function setFormStatus(form, message, isError = false) {
    const status = form.querySelector(".form-status");
    status.textContent = message;
    status.style.color = isError ? "var(--danger)" : "var(--green)";
}

function initializeAuth() {
    const registerForm = document.querySelector("#register-form");
    if (registerForm) {
        registerForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!registerForm.reportValidity()) return;
            const data = new FormData(registerForm);
            const button = registerForm.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await api("/api/auth/register", {
                    method: "POST",
                    body: JSON.stringify({ name: data.get("name"), email: data.get("email"), password: data.get("password") })
                });
                window.location.href = "account.html";
            } catch (error) {
                setFormStatus(registerForm, error.message || "Could not create the account. Please try again.", true);
                button.disabled = false;
            }
        });
    }

    const loginForm = document.querySelector("#login-form");
    if (loginForm) {
        loginForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!loginForm.reportValidity()) return;
            const data = new FormData(loginForm);
            const button = loginForm.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await api("/api/auth/login", {
                    method: "POST",
                    body: JSON.stringify({ email: data.get("email"), password: data.get("password") })
                });
                window.location.href = "account.html";
            } catch (error) {
                setFormStatus(loginForm, error.message || "Could not sign in. Please try again.", true);
                button.disabled = false;
            }
        });
    }
}

async function initializeAccount() {
    const account = document.querySelector("#account-content");
    if (!account) return;
    if (!currentUser) {
        account.innerHTML = `<div class="empty-state"><h2>Sign in to view your account</h2><p>Create an account or sign in to see orders placed with that account.</p><a class="button" href="login.html">Sign in</a> <a class="button button-outline" href="register.html">Create account</a><p class="summary-note">You can place an order as a guest at any time.</p></div>`;
        return;
    }
    account.innerHTML = `<p class="eyebrow">Your account</p><h1>Welcome, ${escapeHTML(currentUser.name)}</h1><p>${escapeHTML(currentUser.email)}</p><p>Loading your orders…</p>`;
    try {
        const orders = await api("/api/orders");
        account.innerHTML = `
            <div class="account-header"><div><p class="eyebrow">Your account</p><h1>Welcome, ${escapeHTML(currentUser.name)}</h1><p>${escapeHTML(currentUser.email)}</p></div><button id="sign-out" class="button button-outline" type="button">Sign out</button></div>
            <h2 class="account-section-title">Your orders</h2>
            ${orders.length ? `<div class="order-list">${orders.map((order) => `
                <article class="order-card">
                    <div class="order-card-head"><h3>Order ${escapeHTML(order.id)}</h3><span>${new Date(order.placedAt).toLocaleDateString()}</span></div>
                    ${order.items.map((item) => `<p>${escapeHTML(item.name)} × ${item.quantity} — ${INR.format(item.price * item.quantity)}</p>`).join("")}
                    <p class="order-card-total">Total: ${INR.format(order.total)}</p><p>No payment was collected.</p>
                </article>`).join("")}</div>` : `<div class="empty-state"><h2>No orders yet</h2><p>Your completed orders will be listed here.</p><a class="button" href="products.html">Start shopping</a></div>`}`;
        document.querySelector("#sign-out").addEventListener("click", async () => {
            try {
                await api("/api/auth/logout", { method: "POST" });
                window.location.href = "landingpage.html";
            } catch (error) {
                showMessage(error.message || "Could not sign out. Please try again.", true);
            }
        });
    } catch (error) {
        account.innerHTML = `<div class="empty-state"><h2>We couldn't load your orders</h2><p>${escapeHTML(error.message)}</p><a class="button" href="landingpage.html">Return home</a></div>`;
    }
}

function initializeContact() {
    const form = document.querySelector("#contact-form");
    if (!form) return;
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const result = await api("/api/contact", {
                method: "POST",
                body: JSON.stringify({ name: data.get("name"), email: data.get("email"), message: data.get("message") })
            });
            form.reset();
            setFormStatus(form, result.message);
        } catch (error) {
            setFormStatus(form, error.message || "Could not save your message. Please try again.", true);
        } finally {
            button.disabled = false;
        }
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    document.querySelector("#live-status")?.setAttribute("aria-live", "polite");
    try {
        await refreshCurrentUser();
    } catch (error) {
        renderChrome();
        showMessage(`Couldn't verify your account: ${error.message}`, true);
    }
    document.body.addEventListener("click", (event) => {
        const addButton = event.target.closest("[data-add-to-cart]");
        const buyLink = event.target.closest("[data-buy-now]");
        if (addButton) addToCart(addButton.dataset.addToCart);
        if (buyLink) {
            event.preventDefault();
            addToCart(buyLink.dataset.buyNow, true);
        }
    });
    initializeHome();
    initializeShop();
    initializeCart();
    initializeAuth();
    initializeAccount();
    initializeContact();
});
