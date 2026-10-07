import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createRoot } from "react-dom/client";
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
import { createApi, type Api, type Row, type Page } from "./api";
import "./style.css";
const tabs = [
  { id: "invoices", name: "Invoices", icon: FileText },
  { id: "customers", name: "Customers", icon: Users },
  { id: "webhook-deliveries", name: "Deliveries", icon: Send },
] as const;
const text = (value: unknown) => (value == null ? "—" : String(value));
function Badge({ value }: { value: unknown }) {
  return (
    <span className={`badge ${text(value)}`}>
      <span />
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
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function App() {
  const [key, setKey] = useState("");
  const [keyInput, setKeyInput] = useState("");
  const [tab, setTab] = useState("invoices");
  const [page, setPage] = useState<Page>({ data: [], next_cursor: null });
  const [cursor, setCursor] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [modal, setModal] = useState<
    "customer" | "invoice" | "endpoint" | null
  >(null);
  const [selected, setSelected] = useState<string | null>(null);
  const api = useMemo(() => createApi(key), [key]);
  const requestVersion = useRef(0);
  const load = useCallback(async () => {
    if (!key) return;
    const version = ++requestVersion.current;
    setBusy(true);
    setError("");
    try {
      const params = new URLSearchParams({ limit: "20" });
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
  function changeTab(next: string) {
    setTab(next);
    setCursor(null);
    setFilter("");
    setSelected(null);
    setPage({ data: [], next_cursor: null });
  }
  const heading = tabs.find((t) => t.id === tab)?.name;
  return (
    <div className="shell">
      <aside>
        <a className="brand" href="#" onClick={(e) => e.preventDefault()}>
          <div className="brand-mark">
            <Layers size={24} />
          </div>
          ledger<span>•</span>
        </a>
        <div className="workspace">
          <div className="avatar">DB</div>
          <div>
            <b>Demo Business</b>
            <small>Billing workspace</small>
          </div>
          <ChevronRight size={15} />
        </div>
        <div className="nav-label">WORKSPACE</div>
        <nav>
          {tabs.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? "active" : ""}
              onClick={() => changeTab(t.id)}
            >
              <t.icon size={19} />
              {t.name}
              {tab === t.id ? <span className="nav-dot" /> : null}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <span className="local-indicator" />
          Local workspace<div>Clear records. Confident payments.</div>
        </div>
      </aside>
      <main>
        <header>
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} /> <span>{heading}</span>
          </div>
          <div className="header-right">
            <span className="environment">SANDBOX</span>
            {key ? (
              <button
                className="text-button"
                onClick={() => {
                  requestVersion.current++;
                  setKey("");
                  setKeyInput("");
                  setError("");
                  setPage({ data: [], next_cursor: null });
                }}
              >
                Disconnect
              </button>
            ) : null}
            <div className="user-avatar">YM</div>
          </div>
        </header>
        <section className="content">
          <div className="eyebrow">YOUR BUSINESS, IN VIEW</div>
          <div className="title-row">
            <div>
              <h1>{heading}</h1>
              <p>
                {tab === "invoices"
                  ? "From first invoice to final confirmation."
                  : tab === "customers"
                    ? "The people and businesses you work with."
                    : "A clear record of every notification."}
              </p>
            </div>
            <button
              className="primary"
              disabled={!key}
              onClick={() =>
                setModal(
                  tab === "invoices"
                    ? "invoice"
                    : tab === "customers"
                      ? "customer"
                      : "endpoint",
                )
              }
            >
              <Plus size={17} />
              {tab === "invoices"
                ? "Create invoice"
                : tab === "customers"
                  ? "Add customer"
                  : "Add endpoint"}
            </button>
          </div>
          {!key ? (
            <section className="connect">
              <div className="connect-icon">
                <KeyRound size={25} />
              </div>
              <div>
                <h2>Connect your workspace</h2>
                <p>
                  Enter the demo API key from the README. Your key stays in this
                  tab’s memory.
                </p>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    setKey(keyInput);
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
                  <button className="primary">
                    Connect <ArrowUpRight size={16} />
                  </button>
                </form>
              </div>
            </section>
          ) : (
            <>
              <section className="overview-strip">
                <div>
                  <CircleDot size={22} />
                  <div>
                    <b>One place for the full story</b>
                    <p>
                      {tab === "invoices"
                        ? "Invoice details, payment attempts, and the latest confirmed state."
                        : tab === "customers"
                          ? "Create a customer, then bring their next invoice to life."
                          : "Delivery outcomes stay separate from your payment records."}
                    </p>
                  </div>
                </div>
                <span className="live-label">
                  <span />
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
                    <RefreshCw size={16} className={busy ? "spin" : ""} />
                  </button>
                </div>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        {(tab === "invoices"
                          ? [
                              "Invoice",
                              "Customer",
                              "Due date",
                              "Amount",
                              "Status",
                              "",
                            ]
                          : tab === "customers"
                            ? ["Customer", "Email", "Created"]
                            : [
                                "Delivery",
                                "Event",
                                "Attempts",
                                "Status",
                                "Last response",
                              ]
                        ).map((h, i) => (
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
                                  INV · {row.id.slice(0, 8)}
                                </button>
                              </td>
                              <td className="mono">
                                {text(row.customer_id).slice(0, 8)}
                              </td>
                              <td>{text(row.due_date)}</td>
                              <td className="amount">
                                {text(row.amount_display)}
                              </td>
                              <td>
                                <Badge value={row.state} />
                              </td>
                              <td>
                                <button
                                  className="icon"
                                  onClick={() => setSelected(row.id)}
                                  aria-label="View invoice"
                                >
                                  <ArrowUpRight size={17} />
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
                              <td>{text(row.created_at).slice(0, 10)}</td>
                            </>
                          ) : (
                            <>
                              <td className="mono">{row.id.slice(0, 8)}</td>
                              <td className="mono">
                                {text(row.event_id).slice(0, 8)}
                              </td>
                              <td>{text(row.attempt_count)}</td>
                              <td>
                                <Badge value={row.status} />
                              </td>
                              <td>
                                {text(
                                  row.last_http_status ?? row.last_error_code,
                                )}
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
                      {tab === "invoices" ? (
                        <FileText size={30} />
                      ) : tab === "customers" ? (
                        <Users size={30} />
                      ) : (
                        <Send size={30} />
                      )}
                    </div>
                    <h3>
                      {busy
                        ? "Loading your records…"
                        : `No ${heading?.toLowerCase()} here yet`}
                    </h3>
                    <p>
                      {tab === "invoices"
                        ? "Start with a customer, then create your first invoice."
                        : tab === "customers"
                          ? "Add your first customer to get started."
                          : "Create an invoice to see its notification deliveries."}
                    </p>
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
                      Next page <ChevronRight size={14} />
                    </button>
                  </div>
                </footer>
              </section>
              <div className="page-note">
                <Check size={14} /> Amounts and payment outcomes come directly
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
          <InvoiceDetail id={selected} api={api} />
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
  kind: "customer" | "invoice" | "endpoint";
  api: Api;
  onDone: () => void;
}) {
  const [customers, setCustomers] = useState<Row[]>([]);
  const [items, setItems] = useState([
    { id: crypto.randomUUID(), description: "", quantity: "1", amount: "" },
  ]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [secret, setSecret] = useState("");
  useEffect(() => {
    if (kind === "invoice") {
      let active = true;
      api<Page>("/customers?limit=100")
        .then((p) => {
          if (active) setCustomers(p.data);
        })
        .catch((e) => {
          if (active) setError(e.message);
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
          { method: "POST", body: JSON.stringify({ url: form.get("url") }) },
        );
        setSecret(result.signing_secret);
      } else {
        await api("/invoices", {
          method: "POST",
          body: JSON.stringify({
            customer_id: form.get("customer"),
            due_date: form.get("date"),
            items: items.map((i) => ({
              description: i.description,
              quantity: i.quantity === "" ? null : Number(i.quantity),
              unit_amount_cents: i.amount === "" ? null : Number(i.amount),
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
            <input name="name" placeholder="Customer or business name" />
          </label>
          <label>
            Email
            <input name="email" type="email" placeholder="name@example.com" />
          </label>
        </>
      ) : kind === "endpoint" ? (
        <>
          <p>Use the destination configured for this local workspace.</p>
          <label>
            Endpoint URL
            <input
              name="url"
              defaultValue="http://demo-receiver:8090/webhooks"
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
            <small>
              Showing up to 100 customers. Create a customer first if this list
              is empty.
            </small>
          </label>
          <label>
            Due date
            <input type="date" name="date" />
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
                    id: crypto.randomUUID(),
                    description: "",
                    quantity: "1",
                    amount: "",
                  },
                ])
              }
            >
              + Add item
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
                          ? { ...i, description: e.target.value }
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
                    value={item.quantity}
                    onChange={(e) =>
                      setItems((v) =>
                        v.map((i) =>
                          i.id === item.id
                            ? { ...i, quantity: e.target.value }
                            : i,
                        ),
                      )
                    }
                  />
                </label>
                <label>
                  Unit price · USD cents
                  <input
                    type="number"
                    value={item.amount}
                    onChange={(e) =>
                      setItems((v) =>
                        v.map((i) =>
                          i.id === item.id
                            ? { ...i, amount: e.target.value }
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
          ? "Saving…"
          : kind === "invoice"
            ? "Create invoice"
            : kind === "customer"
              ? "Save customer"
              : "Register endpoint"}
      </button>
    </form>
  );
}
function InvoiceDetail({ id, api }: { id: string; api: Api }) {
  const [invoice, setInvoice] = useState<Row | null>(null);
  const [attempts, setAttempts] = useState<Row[]>([]);
  const [token, setToken] = useState("tok_success");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [accepted, setAccepted] = useState(false);
  const payment = useRef<{ key: string; token: string } | null>(null);
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function refresh() {
      try {
        const [i, a] = await Promise.all([
          api<Row>(`/invoices/${id}`),
          api<Page>(`/invoices/${id}/payment-attempts`),
        ]);
        if (active) {
          setInvoice(i);
          setAttempts(a.data);
        }
      } catch (e) {
        if (active) setError((e as Error).message);
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
    payment.current ??= { key: crypto.randomUUID(), token };
    try {
      await api(`/invoices/${id}/pay`, {
        method: "POST",
        headers: { "Idempotency-Key": payment.current.key },
        body: JSON.stringify({ card_token: payment.current.token }),
      });
      setAccepted(true);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!invoice)
    return <div className="form-body">{error || "Loading invoice…"}</div>;
  const actions = invoice.allowed_actions as string[];
  return (
    <div className="form-body">
      <div className="invoice-summary">
        <div>
          <small>INVOICE TOTAL</small>
          <h2>{text(invoice.amount_display)}</h2>
        </div>
        <Badge value={invoice.state} />
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
      <label>
        Test payment method
        <select
          value={token}
          disabled={payment.current !== null}
          onChange={(e) => setToken(e.target.value)}
        >
          <option value="tok_success">Successful payment</option>
          <option value="tok_card_declined">Card declined</option>
          <option value="tok_insufficient_funds">Insufficient funds</option>
          <option value="tok_timeout">Delayed confirmation · 30 seconds</option>
          <option value="tok_network_error">Processor error</option>
        </select>
      </label>
      {actions?.includes("pay") || payment.current ? (
        <button
          className="primary full"
          disabled={busy}
          onClick={() => void pay()}
        >
          <CreditCard size={16} />
          {busy
            ? "Submitting…"
            : payment.current
              ? "Retry same request"
              : "Submit payment"}
        </button>
      ) : (
        <p className="muted">
          {text(invoice.payment_block_reason).replaceAll("_", " ")}
        </p>
      )}
      {accepted ? (
        <p className="notice">
          Payment request accepted. The confirmed result appears below.
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
          Start a new payment action
        </button>
      ) : null}
      {error ? (
        <p className="error" role="alert">
          {error}
        </p>
      ) : null}
      <h3>Payment history</h3>
      {attempts.length ? (
        attempts.map((a) => (
          <div className="attempt" key={a.id}>
            <div>
              <Badge value={a.status} />
              <small>{text(a.created_at)}</small>
              {a.failure_code ? (
                <p>{text(a.failure_code).replaceAll("_", " ")}</p>
              ) : null}
              {a.review_required ? (
                <p>Confirmation needs review. Do not submit another payment.</p>
              ) : null}
            </div>
            <code>{a.id.slice(0, 8)}</code>
          </div>
        ))
      ) : (
        <p className="muted">No payment attempts yet.</p>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
