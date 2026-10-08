import React, {useCallback, useEffect, useMemo, useRef, useState,} from "react";
import {createRoot} from "react-dom/client";
import {
    ArrowUpRight,
    Check,
    ChevronRight,
    CircleDot,
    CreditCard,
    FileText,
    KeyRound,
    Layers,
    Plus,
    RefreshCw,
    Send,
    Users,
    X,
} from "./Icons";
import {type Api, createApi, type Page, type Row, uuidv7} from "./api";
import "./style.css";

type Kind = "customer" | "invoice" | "endpoint";
const tabs = [
    {
        id: "invoices",
        name: "Invoices",
        icon: FileText,
        description: "From first invoice to final confirmation.",
        overview: "See each invoice with its payments and webhooks.",
        create: {kind: "invoice", label: "Create invoice"},
        headers: ["Invoice", "Customer", "Due date", "Amount", "Status", ""],
        empty: "Start with a customer, then create your first invoice.",
    },
    {
        id: "customers",
        name: "Customers",
        icon: Users,
        description: "The people and businesses you work with.",
        overview: "Add a customer before you create an invoice.",
        create: {kind: "customer", label: "Add customer"},
        headers: ["Customer", "Email", "Created"],
        empty: "Add your first customer to get started.",
    },
    {
        id: "webhook-deliveries",
        name: "Webhooks",
        icon: Send,
        description: "Webhook messages sent to your systems.",
        overview: "Each message shows whether it reached your endpoint.",
        create: {kind: "endpoint", label: "Add endpoint"},
        headers: ["Event", "Invoice", "Attempts", "Status", "Response"],
        empty: "Webhooks appear here after you create an invoice.",
    },
    {
        id: "events",
        name: "Events",
        icon: CircleDot,
        description: "Every event for your business.",
        overview:
            "Use events to check that nothing was missed.",
        create: null,
        headers: ["Event", "Type", "Invoice", "Created"],
        empty: "Events appear when invoices change.",
    },
] as const;
type TabId = (typeof tabs)[number]["id"];
const eventLabels: Record<string, string> = {
    "invoice.created": "Invoice created",
    "invoice.paid": "Invoice paid",
    "invoice.payment_failed": "Payment failed",
};
const failureLabels: Record<string, string> = {
    card_declined: "The card was declined.",
    insufficient_funds: "The card has insufficient funds.",
    processor_error: "The payment provider could not complete this payment.",
};
const blockLabels: Record<string, string> = {
    invoice_already_paid: "This invoice is already paid.",
    payment_in_progress: "A payment for this invoice is still in progress.",
};
const QUANTITY = /^\d{1,5}$/;
const CENTS = /^\d{1,13}$/;
const label = (labels: Record<string, string>, value: unknown, fallback: string) =>
    labels[String(value)] ?? fallback;
const shortId = (value: unknown) => String(value ?? "").slice(-8);
const when = (value: unknown) => (value ? new Date(String(value)).toLocaleString() : "Not yet");
const text = (value: unknown) => (value == null ? "—" : String(value));

function Badge({value}: { value: unknown }) {
    return (
        <span className={`badge ${text(value)}`}>
      <span/>
            {text(value).replaceAll("_", " ")}
    </span>
    );
}

function Modal({
                   title,
                   children,
                   onClose,
               }: {
    title: string;
    children: React.ReactNode;
    onClose: () => void;
}) {
    const ref = useRef<HTMLDialogElement>(null);
    useEffect(() => {
        ref.current?.showModal();
        return () => ref.current?.close();
    }, []);
    return (
        <dialog ref={ref} onCancel={onClose}>
            <div className="modal-head">
                <h2>{title}</h2>
                <button className="icon" onClick={onClose} aria-label="Close">
                    <X size={20}/>
                </button>
            </div>
            {children}
        </dialog>
    );
}

function App() {
    const [key, setKey] = useState("");
    const [keyInput, setKeyInput] = useState("");
    const [tab, setTab] = useState<TabId>("invoices");
    const [connecting, setConnecting] = useState(false);
    const [connectError, setConnectError] = useState("");
    const [page, setPage] = useState<Page>({data: [], next_cursor: null});
    const [cursor, setCursor] = useState<string | null>(null);
    const [filter, setFilter] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [modal, setModal] = useState<Kind | null>(null);
    const [selected, setSelected] = useState<string | null>(null);
    const api = useMemo(() => createApi(key), [key]);
    const requestVersion = useRef(0);
    const load = useCallback(async () => {
        if (!key) return;
        const version = ++requestVersion.current;
        setBusy(true);
        setError("");
        try {
            const params = new URLSearchParams({limit: "20"});
            if (cursor) params.set("cursor", cursor);
            if (tab === "invoices" && filter) params.set("state", filter);
            const result = await api<Page>(`/${tab}?${params}`);
            if (version === requestVersion.current) setPage(result);
        } catch (e) {
            if (version === requestVersion.current) setError((e as Error).message);
        } finally {
            if (version === requestVersion.current) setBusy(false);
        }
    }, [api, key, tab, cursor, filter]);
    useEffect(() => {
        void load();
        return () => {
            requestVersion.current++;
        };
    }, [load]);

    async function connect(candidate: string) {
        setConnecting(true);
        setConnectError("");
        try {
            await createApi(candidate)<Page>("/customers?limit=1");
            setKey(candidate);
        } catch (e) {
            setConnectError((e as Error).message);
        } finally {
            setConnecting(false);
        }
    }

    function changeTab(next: TabId) {
        setTab(next);
        setCursor(null);
        setFilter("");
        setSelected(null);
        setPage({data: [], next_cursor: null});
    }

    const view = tabs.find((t) => t.id === tab)!;
    const heading = view.name;
    return (
        <div className="shell">
            <aside>
                <a className="brand" href="#" onClick={(e) => e.preventDefault()}>
                    <div className="brand-mark">
                        <Layers size={24}/>
                    </div>
                    ledger<span>•</span>
                </a>
                <div className="workspace">
                    <div className="avatar">API</div>
                    <div>
                        <b>Your business</b>
                        <small>Connected with your API key</small>
                    </div>
                    <ChevronRight size={15}/>
                </div>
                <div className="nav-label">WORKSPACE</div>
                <nav>
                    {tabs.map((t) => (
                        <button
                            key={t.id}
                            className={tab === t.id ? "active" : ""}
                            onClick={() => changeTab(t.id)}
                        >
                            <t.icon size={19}/>
                            {t.name}
                            {tab === t.id ? <span className="nav-dot"/> : null}
                        </button>
                    ))}
                </nav>
                <div className="sidebar-bottom">
                    <span className="local-indicator"/>
                    Local workspace
                    <div>Clear records. Confident payments.</div>
                </div>
            </aside>
            <main>
                <header>
                    <div className="breadcrumb">
                        Workspace <ChevronRight size={14}/> <span>{heading}</span>
                    </div>
                    <div className="header-right">
                        <span className="environment">SANDBOX</span>
                        {key ? (
                            <button
                                className="text-button"
                                onClick={() => {
                                    requestVersion.current++;
                                    setSelected(null);
                                    setModal(null);
                                    setKey("");
                                    setKeyInput("");
                                    setError("");
                                    setPage({data: [], next_cursor: null});
                                }}
                            >
                                Disconnect
                            </button>
                        ) : null}
                    </div>
                </header>
                <section className="content">
                    <div className="eyebrow">YOUR BUSINESS, IN VIEW</div>
                    <div className="title-row">
                        <div>
                            <h1>{heading}</h1>
                            <p>{view.description}</p>
                        </div>
                        {view.create ? (
                            <button
                                className="primary"
                                disabled={!key}
                                onClick={() => setModal(view.create.kind)}
                            >
                                <Plus size={17}/>
                                {view.create.label}
                            </button>
                        ) : null}
                    </div>
                    {!key ? (
                        <section className="connect">
                            <div className="connect-icon">
                                <KeyRound size={25}/>
                            </div>
                            <div>
                                <h2>Connect your workspace</h2>
                                <p>
                                    Enter your API key. It is kept only while this tab is open.
                                </p>
                                <form
                                    onSubmit={(e) => {
                                        e.preventDefault();
                                        void connect(keyInput.trim());
                                    }}
                                >
                                    <input
                                        type="password"
                                        autoComplete="off"
                                        aria-label="API key"
                                        placeholder="Paste your API key"
                                        value={keyInput}
                                        onChange={(e) => setKeyInput(e.target.value)}
                                    />
                                    <button
                                        className="primary"
                                        disabled={connecting || !keyInput}
                                    >
                                        {connecting ? "Checking" : "Connect"}{" "}
                                        <ArrowUpRight size={16}/>
                                    </button>
                                </form>
                                {connectError ? (
                                    <p role="alert" className="error">
                                        {connectError}
                                    </p>
                                ) : null}
                            </div>
                        </section>
                    ) : (
                        <>
                            <section className="overview-strip">
                                <div>
                                    <CircleDot size={22}/>
                                    <div>
                                        <b>One place for the full story</b>
                                        <p>{view.overview}</p>
                                    </div>
                                </div>
                                <span className="live-label">
                  <span/>
                  Connected
                </span>
                            </section>
                            {error ? (
                                <div role="alert" className="error">
                                    {error}
                                </div>
                            ) : null}
                            <section className="table-card">
                                <div className="table-toolbar">
                                    <div className="filter-tabs">
                                        {tab === "invoices" ? (
                                            ["", "open", "paid"].map((s) => (
                                                <button
                                                    key={s}
                                                    className={filter === s ? "chosen" : ""}
                                                    onClick={() => {
                                                        setFilter(s);
                                                        setCursor(null);
                                                    }}
                                                >
                                                    {s || "All invoices"}
                                                </button>
                                            ))
                                        ) : (
                                            <b>{heading}</b>
                                        )}
                                    </div>
                                    <button
                                        className="icon"
                                        aria-label="Refresh list"
                                        onClick={() => void load()}
                                        disabled={busy}
                                    >
                                        <RefreshCw size={16} className={busy ? "spin" : ""}/>
                                    </button>
                                </div>
                                <div className="table-scroll">
                                    <table>
                                        <thead>
                                        <tr>
                                            {view.headers.map((h, i) => (
                                                <th key={i}>{h}</th>
                                            ))}
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {page.data.map((row) => (
                                            <tr key={row.id}>
                                                {tab === "invoices" ? (
                                                    <>
                                                        <td>
                                                            <button
                                                                className="text-button"
                                                                onClick={() => setSelected(row.id)}
                                                            >
                                                                INV {shortId(row.id)}
                                                            </button>
                                                        </td>
                                                        <td className="mono">
                                                            {shortId(row.customer_id)}
                                                        </td>
                                                        <td>{text(row.due_date)}</td>
                                                        <td className="amount">
                                                            {text(row.amount_display)}
                                                        </td>
                                                        <td>
                                                            <Badge value={row.state}/>
                                                        </td>
                                                        <td>
                                                            <button
                                                                className="icon"
                                                                onClick={() => setSelected(row.id)}
                                                                aria-label="View invoice"
                                                            >
                                                                <ArrowUpRight size={17}/>
                                                            </button>
                                                        </td>
                                                    </>
                                                ) : tab === "customers" ? (
                                                    <>
                                                        <td>
                                                            <div className="person">
                                                                <div className="initial">
                                                                    {text(row.name).slice(0, 1)}
                                                                </div>
                                                                <b>{text(row.name)}</b>
                                                            </div>
                                                        </td>
                                                        <td>{text(row.email)}</td>
                                                        <td>{new Date(String(row.created_at)).toLocaleDateString()}</td>
                                                    </>
                                                ) : tab === "events" ? (
                                                    <>
                                                        <td className="mono">{shortId(row.id)}</td>
                                                        <td className="mono">{text(row.event_type)}</td>
                                                        <td className="mono">
                                                            {shortId(
                                                                (
                                                                    row.payload as {
                                                                        data?: { invoice_id?: string };
                                                                    }
                                                                )?.data?.invoice_id,
                                                            )}
                                                        </td>
                                                        <td>
                                                            {when(row.created_at)}
                                                        </td>
                                                    </>
                                                ) : (
                                                    <>
                                                        <td>{label(eventLabels, row.event_type, "Event")}</td>
                                                        <td className="mono">
                                                            {shortId(row.invoice_id)}
                                                        </td>
                                                        <td>{text(row.attempt_count)}</td>
                                                        <td>
                                                            <Badge value={row.status}/>
                                                        </td>
                                                        <td>
                                                            {row.last_http_status == null ? "No response" : text(row.last_http_status)}
                                                        </td>
                                                    </>
                                                )}
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                                {page.data.length === 0 ? (
                                    <div className="empty">
                                        <div className="empty-icon">
                                            <view.icon size={30}/>
                                        </div>
                                        <h3>
                                            {busy
                                                ? "Loading"
                                                : `No ${heading?.toLowerCase()} here yet`}
                                        </h3>
                                        <p>{view.empty}</p>
                                    </div>
                                ) : null}
                                <footer className="table-footer">
                                    <span>{page.data.length} records on this page</span>
                                    <div>
                                        <button onClick={() => setCursor(null)} disabled={!cursor}>
                                            First page
                                        </button>
                                        <button
                                            disabled={!page.next_cursor}
                                            onClick={() => setCursor(page.next_cursor)}
                                        >
                                            Next page <ChevronRight size={14}/>
                                        </button>
                                    </div>
                                </footer>
                            </section>
                            <div className="page-note">
                                <Check size={14}/> Amounts and payment outcomes come directly
                                from your billing service.
                            </div>
                        </>
                    )}
                </section>
            </main>
            {modal ? (
                <Modal
                    title={
                        modal === "invoice"
                            ? "Create invoice"
                            : modal === "customer"
                                ? "Add customer"
                                : "Register endpoint"
                    }
                    onClose={() => setModal(null)}
                >
                    <CreateForm
                        kind={modal}
                        api={api}
                        onDone={() => {
                            setModal(null);
                            void load();
                        }}
                    />
                </Modal>
            ) : null}
            {selected ? (
                <Modal
                    title="Invoice details"
                    onClose={() => {
                        setSelected(null);
                        void load();
                    }}
                >
                    <InvoiceDetail id={selected} api={api}/>
                </Modal>
            ) : null}
        </div>
    );
}

function CreateForm({
                        kind,
                        api,
                        onDone,
                    }: {
    kind: Kind;
    api: Api;
    onDone: () => void;
}) {
    const [customers, setCustomers] = useState<Row[]>([]);
    const [items, setItems] = useState([
        {id: uuidv7(), description: "", quantity: "1", amount: ""},
    ]);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [secret, setSecret] = useState("");
    useEffect(() => {
        if (kind === "invoice") {
            let active = true;
            (async () => {
                const all: Row[] = [];
                let cursor: string | null = null;
                do {
                    const params = new URLSearchParams({limit: "100"});
                    if (cursor) params.set("cursor", cursor);
                    const page: Page = await api<Page>(`/customers?${params}`);
                    all.push(...page.data);
                    cursor = page.next_cursor;
                } while (cursor && active);
                if (active) setCustomers(all);
            })().catch((e) => {
                if (active) setError((e as Error).message);
            });
            return () => {
                active = false;
            };
        }
    }, [kind, api]);

    async function submit(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        setBusy(true);
        setError("");
        const form = new FormData(e.currentTarget);
        try {
            if (kind === "customer") {
                await api("/customers", {
                    method: "POST",
                    body: JSON.stringify({
                        name: form.get("name"),
                        email: form.get("email"),
                    }),
                });
                onDone();
            } else if (kind === "endpoint") {
                const result = await api<{ signing_secret: string }>(
                    "/webhook-endpoints",
                    {method: "POST", body: JSON.stringify({url: form.get("url")})},
                );
                setSecret(result.signing_secret);
            } else {
                if (items.some((i) => !QUANTITY.test(i.quantity) || !CENTS.test(i.amount)))
                    throw new Error("Use whole numbers for quantity and price.");
                await api("/invoices", {
                    method: "POST",
                    body: JSON.stringify({
                        customer_id: form.get("customer"),
                        due_date: form.get("date"),
                        items: items.map((i) => ({
                            description: i.description,
                            quantity: Number.parseInt(i.quantity, 10),
                            unit_amount_cents: Number.parseInt(i.amount, 10),
                        })),
                    }),
                });
                onDone();
            }
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setBusy(false);
        }
    }

    if (secret)
        return (
            <div className="form-body">
                <p>Save this signing secret. It is shown once.</p>
                <code className="secret">{secret}</code>
                <button className="primary" onClick={onDone}>
                    Done
                </button>
            </div>
        );
    return (
        <form className="form-body" onSubmit={submit}>
            {kind === "customer" ? (
                <>
                    <label>
                        Name
                        <input name="name" placeholder="Customer or business name"/>
                    </label>
                    <label>
                        Email
                        <input name="email" type="email" placeholder="name@example.com"/>
                    </label>
                </>
            ) : kind === "endpoint" ? (
                <>
                    <p>
                        Use a public HTTPS address. The demo receiver is already registered.
                    </p>
                    <label>
                        Endpoint URL
                        <input
                            name="url"
                            type="url"
                            placeholder="https://example.com/webhooks"
                        />
                    </label>
                </>
            ) : (
                <>
                    <label>
                        Customer
                        <select name="customer" defaultValue="">
                            <option value="">Select a customer</option>
                            {customers.map((c) => (
                                <option key={c.id} value={c.id}>
                                    {text(c.name)}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        Due date
                        <input type="date" name="date"/>
                    </label>
                    <div className="items-title">
                        <b>Line items</b>
                        <button
                            type="button"
                            className="text-button"
                            onClick={() =>
                                setItems((v) => [
                                    ...v,
                                    {
                                        id: uuidv7(),
                                        description: "",
                                        quantity: "1",
                                        amount: "",
                                    },
                                ])
                            }
                        >
                            Add item
                        </button>
                    </div>
                    {items.map((item, index) => (
                        <div className="item-form" key={item.id}>
                            <label>
                                Description
                                <input
                                    value={item.description}
                                    onChange={(e) =>
                                        setItems((v) =>
                                            v.map((i) =>
                                                i.id === item.id
                                                    ? {...i, description: e.target.value}
                                                    : i,
                                            ),
                                        )
                                    }
                                />
                            </label>
                            <div className="two-cols">
                                <label>
                                    Quantity
                                    <input
                                        type="number"
                                        min={1}
                                        step={1}
                                        inputMode="numeric"
                                        value={item.quantity}
                                        onChange={(e) =>
                                            setItems((v) =>
                                                v.map((i) =>
                                                    i.id === item.id
                                                        ? {...i, quantity: e.target.value}
                                                        : i,
                                                ),
                                            )
                                        }
                                    />
                                </label>
                                <label>
                                    Unit price in cents
                                    <input
                                        type="number"
                                        min={0}
                                        step={1}
                                        inputMode="numeric"
                                        value={item.amount}
                                        onChange={(e) =>
                                            setItems((v) =>
                                                v.map((i) =>
                                                    i.id === item.id
                                                        ? {...i, amount: e.target.value}
                                                        : i,
                                                ),
                                            )
                                        }
                                    />
                                </label>
                            </div>
                            {index > 0 ? (
                                <button
                                    type="button"
                                    className="text-button"
                                    onClick={() =>
                                        setItems((v) => v.filter((i) => i.id !== item.id))
                                    }
                                >
                                    Remove item
                                </button>
                            ) : null}
                        </div>
                    ))}
                    <p className="muted">
                        The final total is calculated when your invoice is created.
                    </p>
                </>
            )}
            {error ? (
                <p role="alert" className="error">
                    {error}
                </p>
            ) : null}
            <button className="primary full" disabled={busy}>
                {busy
                    ? "Saving"
                    : kind === "invoice"
                        ? "Create invoice"
                        : kind === "customer"
                            ? "Save customer"
                            : "Register endpoint"}
            </button>
        </form>
    );
}

function InvoiceDetail({id, api}: { id: string; api: Api }) {
    const [invoice, setInvoice] = useState<Row | null>(null);
    const [attempts, setAttempts] = useState<Row[]>([]);
    const [webhooks, setWebhooks] = useState<Row[]>([]);
    const [token, setToken] = useState("tok_success");
    const [error, setError] = useState("");
    const [loadError, setLoadError] = useState("");
    const [busy, setBusy] = useState(false);
    const [accepted, setAccepted] = useState(false);
    const payment = useRef<{ key: string; token: string } | null>(null);
    useEffect(() => {
        let active = true;
        let timer: ReturnType<typeof setTimeout>;

        async function refresh() {
            try {
                const [i, a, w] = await Promise.all([
                    api<Row>(`/invoices/${id}`),
                    api<Page>(`/invoices/${id}/payment-attempts`),
                    api<Page>(`/webhook-deliveries?invoice_id=${id}`),
                ]);
                if (active) {
                    setInvoice(i);
                    setAttempts(a.data);
                    setWebhooks(w.data);
                    setLoadError("");
                }
            } catch (e) {
                if (active) setLoadError((e as Error).message);
            } finally {
                if (active) timer = setTimeout(refresh, 2000);
            }
        }

        void refresh();
        return () => {
            active = false;
            clearTimeout(timer);
        };
    }, [api, id]);

    async function pay() {
        setBusy(true);
        setError("");
        payment.current ??= {key: uuidv7(), token};
        try {
            await api(`/invoices/${id}/pay`, {
                method: "POST",
                headers: {"Idempotency-Key": payment.current.key},
                body: JSON.stringify({card_token: payment.current.token}),
            });
            setAccepted(true);
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setBusy(false);
        }
    }

    if (!invoice)
        return <div className="form-body">{loadError || "Loading"}</div>;
    const actions = invoice.allowed_actions as string[];
    const canPay = actions?.includes("pay") || (payment.current !== null && invoice.state !== "paid");
    return (
        <div className="form-body">
            <div className="invoice-summary">
                <div>
                    <small>INVOICE TOTAL</small>
                    <h2>{text(invoice.amount_display)}</h2>
                </div>
                <Badge value={invoice.state}/>
            </div>
            <div className="detail-meta">
                <span>Due {text(invoice.due_date)}</span>
                <code>{id}</code>
            </div>
            <div className="line-items">
                {(invoice.items as Row[]).map((item, i) => (
                    <div key={i}>
            <span>
              {text(item.description)}
                <small>Quantity {text(item.quantity)}</small>
            </span>
                        <b>{text(item.unit_amount_display)} / unit</b>
                    </div>
                ))}
            </div>
            <h3>Payment</h3>
            <label hidden={!canPay}>
                Test payment method
                <select
                    value={token}
                    disabled={payment.current !== null}
                    onChange={(e) => setToken(e.target.value)}
                >
                    <option value="tok_success">Successful payment</option>
                    <option value="tok_card_declined">Card declined</option>
                    <option value="tok_insufficient_funds">Insufficient funds</option>
                    <option value="tok_timeout">Slow confirmation</option>
                    <option value="tok_network_error">Payment provider error</option>
                </select>
            </label>
            {canPay ? (
                <button
                    className="primary full"
                    disabled={busy}
                    onClick={() => void pay()}
                >
                    <CreditCard size={16}/>
                    {busy
                        ? "Sending"
                        : payment.current
                            ? "Send again"
                            : "Pay"}
                </button>
            ) : (
                <p className="muted">
                    {label(blockLabels, invoice.payment_block_reason, "This invoice cannot be paid right now.")}
                </p>
            )}
            {accepted ? (
                <p className="notice">
                    Payment started. The result will appear below.
                </p>
            ) : null}
            {payment.current && actions?.includes("pay") ? (
                <button
                    className="text-button"
                    onClick={() => {
                        payment.current = null;
                        setAccepted(false);
                        setError("");
                        setToken("tok_success");
                    }}
                >
                    Start a new payment
                </button>
            ) : null}
            {error || loadError ? (
                <p className="error" role="alert">
                    {error || loadError}
                </p>
            ) : null}
            <h3>Payment history</h3>
            {attempts.length ? (
                attempts.map((a) => (
                    <div className="attempt" key={a.id}>
                        <div>
                            <Badge value={a.status}/>
                            <small>{when(a.created_at)}</small>
                            {a.failure_code ? (
                                <p>{label(failureLabels, a.failure_code, "The payment failed.")}</p>
                            ) : null}
                            {a.review_required ? (
                                <p>This payment is taking longer than usual. We keep checking with the payment provider.</p>
                            ) : a.status === "unknown" ? (
                                <p>We are still confirming this payment.</p>
                            ) : null}
                        </div>
                        <code>{shortId(a.id)}</code>
                    </div>
                ))
            ) : (
                <p className="muted">No payments yet.</p>
            )}
            <h3>Webhooks</h3>
            {webhooks.length ? (
                webhooks.map((w) => (
                    <div className="attempt" key={w.id}>
                        <div>
                            <Badge value={w.status}/>
                            <b>{label(eventLabels, w.event_type, "Event")}</b>
                            <small>
                                {Number(w.attempt_count) === 1 ? "1 attempt" : `${text(w.attempt_count)} attempts`}
                                {w.delivered_at ? ` and delivered ${when(w.delivered_at)}` : ""}
                            </small>
                            {w.status === "exhausted" ? (
                                <p>Delivery stopped after all retries.</p>
                            ) : w.status === "pending" && Number(w.attempt_count) > 0 ? (
                                <p>Your endpoint did not answer. We will try again.</p>
                            ) : null}
                        </div>
                        <code>{w.last_http_status == null ? "No response" : text(w.last_http_status)}</code>
                    </div>
                ))
            ) : (
                <p className="muted">No webhooks yet.</p>
            )}
        </div>
    );
}

createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <App/>
    </React.StrictMode>,
);
