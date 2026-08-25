import sys
import threading
import winsound
import os
import copy
import json
import socket
import subprocess

from datetime import datetime
from typing import List, Optional, Dict

from PyQt6.QtWidgets import (
    QApplication,
    QMainWindow,
    QWidget,
    QVBoxLayout,
    QHBoxLayout,
    QGridLayout,
    QPushButton,
    QLabel,
    QListWidget,
    QDialog,
    QComboBox,
    QLineEdit,
    QMessageBox,
    QFrame,
    QInputDialog,
    QTabWidget,
    QFormLayout,
    QScrollArea,
    QTableWidget,
    QTableWidgetItem,
    QHeaderView,
    QAbstractItemView
)

from PyQt6.QtCore import Qt, QTimer
import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# ============================================================
# DATOS DEL DESARROLLADOR Y CONFIGURACIÓN
# ============================================================

DEVELOPER_NAME = "Sureshum"
DEVELOPER_CONTACT = "ssshum25ssshum25@gmail.com"

APP_VERSION = "v1.7.0"

CARPETA_RECIBOS = "recibos"
DATA_FILE = "pos_database.json"

SERVER_HOST = "0.0.0.0"
SERVER_PORT = 5000

IVA_PERCENTAGE = 16.0

db_lock = threading.RLock()


def get_local_ip() -> str:
    """Obtiene la dirección IP local de la computadora para la red Wi-Fi."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


# ============================================================
# MODELOS PYDANTIC PARA FASTAPI
# ============================================================

class ItemOrder(BaseModel):
    nombre: str
    cantidad: int
    precio: float


class OrderPayload(BaseModel):
    mesa: str
    camarero: Optional[str] = ""
    items: List[ItemOrder] = []
    areaId: Optional[int] = None


class AreaSyncPayload(BaseModel):
    id: Optional[int] = None
    name: str
    prefix: str = "M"


class TableCountPayload(BaseModel):
    areaId: Optional[int] = None
    count: int


class ProductPayload(BaseModel):
    id: Optional[int] = None
    category: str = "General"
    name: str
    price: float


# ============================================================
# BASE DE DATOS MADRE (SERVIDOR PRINCIPAL)
# ============================================================

DEFAULT_ROOMS_DB: Dict[int, dict] = {
    1: {
        "id": 1,
        "name": "Salon Principal",
        "prefix": "M",
        "count": 10,
        "mesas": {
            f"Mesa {i}": {
                "number": i,
                "camarero": "",
                "items": [],
                "total": 0.0
            }
            for i in range(1, 11)
        }
    }
}

DEFAULT_PRODUCTS: List[dict] = [
    {"id": 1, "category": "Comida", "name": "Hamburguesa Clásica", "price": 85.0},
    {"id": 2, "category": "Comida", "name": "Pizza Pepperoni", "price": 120.0},
    {"id": 3, "category": "Comida", "name": "Papas Fritas", "price": 45.0},
    {"id": 4, "category": "Comida", "name": "Tacos de Bistec (3pz)", "price": 60.0},
    {"id": 5, "category": "Bebidas", "name": "Refresco Coca-Cola 600ml", "price": 25.0},
    {"id": 6, "category": "Bebidas", "name": "Agua Fresca Natural", "price": 20.0},
    {"id": 7, "category": "Bebidas", "name": "Cerveza Corona", "price": 35.0},
    {"id": 8, "category": "Postres", "name": "Rebanada de Pastel", "price": 40.0}
]

rooms_db: Dict[int, dict] = {}
products_db: List[dict] = []
last_sync_version = 0


def normalize_rooms_db():
    """Garantiza que cada sala tenga exactamente sus mesas 1..count sin duplicados de claves."""
    with db_lock:
        for area_id, room in list(rooms_db.items()):
            count = max(1, int(room.get("count", 10)))
            room["count"] = count

            # Normalizar prefijo
            prefix = room.get("prefix", "M").strip()
            room["prefix"] = prefix if prefix else "M"

            # Recolectar datos existentes por número entero de mesa
            tables_by_number = {}
            for key, val in room.get("mesas", {}).items():
                num = val.get("number")
                if num is None or num == 0:
                    digits = ''.join(filter(str.isdigit, str(key)))
                    num = int(digits) if digits else 1

                if num not in tables_by_number:
                    val["number"] = num
                    tables_by_number[num] = val
                else:
                    # Si hay dos entradas para el mismo número, conservar la que tenga pedidos activos
                    if len(val.get("items", [])) > len(tables_by_number[num].get("items", [])):
                        val["number"] = num
                        tables_by_number[num] = val

            # Reconstruir estrictamente las mesas 1..count
            clean_mesas = {}
            for i in range(1, count + 1):
                if i in tables_by_number:
                    clean_mesas[f"Mesa {i}"] = tables_by_number[i]
                else:
                    clean_mesas[f"Mesa {i}"] = {
                        "number": i,
                        "camarero": "",
                        "items": [],
                        "total": 0.0
                    }

            room["mesas"] = clean_mesas


def save_database():
    with db_lock:
        try:
            normalize_rooms_db()
            payload = {
                "rooms": rooms_db,
                "products": products_db
            }
            with open(DATA_FILE, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"Error al guardar la base de datos: {e}")


def load_database():
    global rooms_db, products_db
    with db_lock:
        if os.path.exists(DATA_FILE):
            try:
                with open(DATA_FILE, "r", encoding="utf-8") as f:
                    raw_data = json.load(f)

                    if "rooms" in raw_data:
                        rooms_db = {int(k): v for k, v in raw_data["rooms"].items()}
                        products_db = raw_data.get("products", copy.deepcopy(DEFAULT_PRODUCTS))
                    else:
                        rooms_db = {int(k): v for k, v in raw_data.items()}
                        products_db = copy.deepcopy(DEFAULT_PRODUCTS)

                    normalize_rooms_db()
                    return
            except Exception as e:
                print(f"Error al cargar base de datos: {e}")

        rooms_db = copy.deepcopy(DEFAULT_ROOMS_DB)
        products_db = copy.deepcopy(DEFAULT_PRODUCTS)
        save_database()


def mark_database_changed():
    global last_sync_version
    with db_lock:
        last_sync_version += 1
        save_database()


load_database()


# ============================================================
# BÚSQUEDA Y MANIPULACIÓN EXACTA DE MESAS
# ============================================================

def find_table_in_rooms(table_identifier: str, preferred_area_id: Optional[int] = None):
    """
    Localiza la mesa exacta y su sala correspondiente sin crear duplicados.
    """
    if not table_identifier:
        return None, None, None, None

    with db_lock:
        digits = ''.join(filter(str.isdigit, str(table_identifier)))
        target_num = int(digits) if digits else 1

        # 1. Si se pasa preferred_area_id, buscar directamente en esa sala
        if preferred_area_id is not None and preferred_area_id in rooms_db:
            r_data = rooms_db[preferred_area_id]
            t_key = f"Mesa {target_num}"
            if t_key not in r_data["mesas"]:
                r_data["mesas"][t_key] = {
                    "number": target_num,
                    "camarero": "",
                    "items": [],
                    "total": 0.0
                }
            return preferred_area_id, r_data["name"], t_key, r_data["mesas"][t_key]

        # 2. Buscar por coincidencia de prefijo
        clean_id = str(table_identifier).strip().lower()
        for area_id, room_data in rooms_db.items():
            pfx = room_data.get("prefix", "").strip().lower()
            if pfx and clean_id.startswith(pfx):
                t_key = f"Mesa {target_num}"
                if t_key not in room_data["mesas"]:
                    room_data["mesas"][t_key] = {
                        "number": target_num,
                        "camarero": "",
                        "items": [],
                        "total": 0.0
                    }
                return area_id, room_data["name"], t_key, room_data["mesas"][t_key]

        # 3. Fallback: Sala por defecto (primera sala)
        first_area_id = list(rooms_db.keys())[0] if rooms_db else 1
        if first_area_id in rooms_db:
            r_data = rooms_db[first_area_id]
            t_key = f"Mesa {target_num}"
            if t_key not in r_data["mesas"]:
                r_data["mesas"][t_key] = {
                    "number": target_num,
                    "camarero": "",
                    "items": [],
                    "total": 0.0
                }
            return first_area_id, r_data["name"], t_key, r_data["mesas"][t_key]

        return None, None, None, None


def update_occupied_status(table_data: dict):
    table_data["total"] = sum(
        item["cantidad"] * item["precio"]
        for item in table_data.get("items", [])
    )


def update_room_tables_count(area_id: int, new_count: int):
    with db_lock:
        if area_id not in rooms_db or new_count < 1:
            return

        room = rooms_db[area_id]
        room["count"] = new_count
        normalize_rooms_db()
        mark_database_changed()


# ============================================================
# SERIALIZACIÓN PARA CLIENTES ANDROID
# ============================================================

def serialize_areas():
    with db_lock:
        normalize_rooms_db()
        return [
            {
                "id": area["id"],
                "name": area["name"],
                "prefix": area.get("prefix", "M"),
                "count": area.get("count", 10)
            }
            for area in rooms_db.values()
        ]


def serialize_tables_dict(area_id: Optional[int] = None) -> Dict[str, dict]:
    """Retorna las mesas indexadas para que Android pueda leerlas con total compatibilidad."""
    with db_lock:
        normalize_rooms_db()
        result = {}

        target_areas = [rooms_db[area_id]] if (area_id is not None and area_id in rooms_db) else list(rooms_db.values())

        for room in target_areas:
            prefix = room.get("prefix", "M").strip()
            room_id = room["id"]

            for i in range(1, room.get("count", 10) + 1):
                table_key = f"Mesa {i}"
                table_data = room["mesas"].get(table_key, {
                    "number": i,
                    "camarero": "",
                    "items": [],
                    "total": 0.0
                })

                is_occ = len(table_data.get("items", [])) > 0
                tot = float(table_data.get("total", 0.0))

                t_dict = {
                    "id": i,
                    "areaId": room_id,
                    "number": i,
                    "camarero": table_data.get("camarero", ""),
                    "items": copy.deepcopy(table_data.get("items", [])),
                    "total": tot,
                    "currentTotal": tot,
                    "isOccupied": is_occ
                }

                result[table_key] = t_dict
                result[f"Mesa {i}"] = t_dict
                result[str(i)] = t_dict

                if prefix and prefix.upper() != "M":
                    result[f"{prefix}{i}"] = t_dict
                    result[f"{prefix} {i}"] = t_dict

        return result


# ============================================================
# GENERADOR DE RECIBOS PDF
# ============================================================

def generar_recibo_pdf(
    nombre_mesa: str,
    camarero: str,
    items: list,
    subtotal: float,
    iva_monto: float,
    total: float,
    efectivo: float,
    tarjeta: float,
    cambio: float
) -> str:
    os.makedirs(CARPETA_RECIBOS, exist_ok=True)

    sanitized_mesa = "".join(c for c in nombre_mesa if c.isalnum() or c in (" ", "_", "-")).strip()
    nombre_archivo = f"Recibo_{sanitized_mesa.replace(' ', '_')}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pdf"
    ruta_completa = os.path.join(CARPETA_RECIBOS, nombre_archivo)

    from reportlab.lib.pagesizes import letter
    from reportlab.pdfgen import canvas

    c = canvas.Canvas(ruta_completa, pagesize=letter)

    y = 750
    c.setFont("Helvetica-Bold", 16)
    c.drawCentredString(306, y, "--- RESTAURANTE POS ---")
    y -= 35

    c.setFont("Helvetica", 12)
    c.drawString(80, y, f"Mesa: {nombre_mesa}")
    c.drawString(320, y, f"Atendió: {camarero or 'Caja'}")
    y -= 20

    c.drawString(80, y, f"Fecha: {datetime.now().strftime('%d/%m/%Y %H:%M:%S')}")
    y -= 30

    c.setFont("Helvetica-Bold", 12)
    c.drawString(80, y, "Producto")
    c.drawString(310, y, "Cant.")
    c.drawString(390, y, "Precio")
    c.drawString(470, y, "Subtotal")
    y -= 12

    c.setLineWidth(1)
    c.line(80, y, 532, y)
    y -= 20

    c.setFont("Helvetica", 11)
    for item in items:
        p_subtotal = item["cantidad"] * item["precio"]
        c.drawString(80, y, str(item["nombre"])[:28])
        c.drawString(315, y, str(item["cantidad"]))
        c.drawString(390, y, f"${item['precio']:.2f}")
        c.drawString(470, y, f"${p_subtotal:.2f}")
        y -= 18

        if y < 100:
            c.showPage()
            y = 750
            c.setFont("Helvetica", 11)

    y -= 5
    c.line(80, y, 532, y)
    y -= 22

    c.setFont("Helvetica", 11)
    c.drawString(340, y, f"Subtotal Base: ${subtotal:.2f}")
    y -= 16
    c.drawString(340, y, f"IVA ({IVA_PERCENTAGE:.1f}%): ${iva_monto:.2f}")
    y -= 22

    c.setFont("Helvetica-Bold", 14)
    c.drawString(340, y, f"TOTAL: ${total:.2f}")
    y -= 22

    c.setFont("Helvetica", 11)
    c.drawString(340, y, f"Efectivo: ${efectivo:.2f}")
    y -= 16
    c.drawString(340, y, f"Tarjeta: ${tarjeta:.2f}")
    y -= 16
    c.drawString(340, y, f"Cambio: ${cambio:.2f}")

    c.setFont("Helvetica-Oblique", 8)
    c.drawCentredString(306, 30, f"Software desarrollado por {DEVELOPER_NAME} - Sistema RestaurantePOS")

    c.save()
    return ruta_completa


# ============================================================
# SERVIDOR FASTAPI Y ENDPOINTS DE SINCRONIZACIÓN
# ============================================================

api = FastAPI(
    title="RestaurantePOS Sync API",
    version=APP_VERSION
)

api.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@api.get("/")
def health_check():
    return {
        "status": "ok",
        "app": "RestaurantePOS",
        "version": APP_VERSION,
        "sync_version": last_sync_version,
        "local_ip": get_local_ip()
    }


@api.get("/areas")
def get_areas():
    return serialize_areas()


@api.post("/areas")
def insert_area(payload: AreaSyncPayload):
    with db_lock:
        clean_name = payload.name.strip()
        clean_prefix = payload.prefix.strip() or "M"

        # Si ya existe una sala con el mismo nombre, actualizar prefijo
        for existing_id, existing_data in rooms_db.items():
            if existing_data["name"].strip().lower() == clean_name.lower():
                existing_data["prefix"] = clean_prefix
                mark_database_changed()
                return {
                    "id": existing_id,
                    "name": existing_data["name"],
                    "prefix": existing_data["prefix"],
                    "count": existing_data.get("count", 10)
                }

        new_id = payload.id
        if new_id is None or new_id in rooms_db:
            new_id = max(rooms_db.keys(), default=0) + 1

        rooms_db[new_id] = {
            "id": new_id,
            "name": clean_name,
            "prefix": clean_prefix,
            "count": 10,
            "mesas": {
                f"Mesa {i}": {
                    "number": i,
                    "camarero": "",
                    "items": [],
                    "total": 0.0
                }
                for i in range(1, 11)
            }
        }
        mark_database_changed()

    return {
        "id": new_id,
        "name": clean_name,
        "prefix": clean_prefix,
        "count": 10
    }


@api.delete("/areas/{area_id}")
def delete_area_endpoint(area_id: int):
    with db_lock:
        if area_id in rooms_db:
            del rooms_db[area_id]
            mark_database_changed()
            return {"status": "ok", "deleted": area_id}

    raise HTTPException(status_code=404, detail="Área no encontrada.")


@api.get("/tables")
def get_tables(areaId: Optional[int] = None):
    return serialize_tables_dict(areaId)


@api.post("/set-tables-count")
def set_tables_count(payload: TableCountPayload):
    with db_lock:
        area_id = payload.areaId
        if area_id is None:
            if not rooms_db:
                raise HTTPException(status_code=404, detail="No existen áreas.")
            area_id = list(rooms_db.keys())[0]

        if area_id not in rooms_db:
            raise HTTPException(status_code=404, detail="Área no encontrada.")

        if payload.count < 1:
            raise HTTPException(status_code=400, detail="La cantidad debe ser mayor que cero.")

        update_room_tables_count(area_id, payload.count)

    return {
        "status": "ok",
        "areaId": area_id,
        "count": payload.count
    }


@api.post("/order")
def receive_order(payload: OrderPayload):
    global last_sync_version

    with db_lock:
        area_id, area_name, table_key, table_data = find_table_in_rooms(
            payload.mesa,
            preferred_area_id=payload.areaId
        )

        if table_data is None:
            raise HTTPException(status_code=404, detail="No se pudo localizar la mesa.")

        if not payload.items:
            table_data["items"] = []
            table_data["camarero"] = ""
            table_data["total"] = 0.0
        else:
            table_data["camarero"] = payload.camarero or ""
            table_data["items"] = [
                {
                    "nombre": item.nombre,
                    "cantidad": item.cantidad,
                    "precio": item.precio
                }
                for item in payload.items
            ]
            update_occupied_status(table_data)

        mark_database_changed()

    return {
        "status": "ok",
        "message": "Comanda procesada",
        "sync_version": last_sync_version
    }


@api.get("/products")
def get_products():
    with db_lock:
        return products_db


@api.post("/products")
def create_product_endpoint(payload: ProductPayload):
    with db_lock:
        new_id = payload.id
        if new_id is None or any(p["id"] == new_id for p in products_db):
            new_id = max([p["id"] for p in products_db], default=0) + 1

        new_prod = {
            "id": new_id,
            "category": payload.category.strip() or "General",
            "name": payload.name.strip(),
            "price": float(payload.price)
        }
        products_db.append(new_prod)
        mark_database_changed()
        return new_prod


@api.put("/products/{product_id}")
def update_product_endpoint(product_id: int, payload: ProductPayload):
    with db_lock:
        for p in products_db:
            if p["id"] == product_id:
                p["category"] = payload.category.strip() or "General"
                p["name"] = payload.name.strip()
                p["price"] = float(payload.price)
                mark_database_changed()
                return p

    raise HTTPException(status_code=404, detail="Producto no encontrado.")


@api.delete("/products/{product_id}")
def delete_product_endpoint(product_id: int):
    with db_lock:
        for i, p in enumerate(products_db):
            if p["id"] == product_id:
                deleted = products_db.pop(i)
                mark_database_changed()
                return {"status": "ok", "deleted": deleted}

    raise HTTPException(status_code=404, detail="Producto no encontrado.")


@api.post("/upload-pdf")
async def upload_pdf(
    file: UploadFile = File(...),
    table_id: Optional[str] = Form(None)
):
    try:
        os.makedirs(CARPETA_RECIBOS, exist_ok=True)
        filename = file.filename or f"Recibo_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pdf"
        safe_filename = os.path.basename(filename)
        file_path = os.path.join(CARPETA_RECIBOS, safe_filename)

        contents = await file.read()
        with open(file_path, "wb") as f:
            f.write(contents)

        if table_id is not None:
            with db_lock:
                _, _, _, table_data = find_table_in_rooms(str(table_id))
                if table_data:
                    table_data["items"] = []
                    table_data["camarero"] = ""
                    table_data["total"] = 0.0
                    mark_database_changed()

        return {
            "status": "ok",
            "filename": safe_filename
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================
# SERVIDOR UVICORN EN HILO SECUNDARIO
# ============================================================

def run_server():
    config = uvicorn.Config(
        api,
        host=SERVER_HOST,
        port=SERVER_PORT,
        log_level="error",
        loop="asyncio"
    )
    server = uvicorn.Server(config)
    server.run()


# ============================================================
# DIÁLOGO DE GESTIÓN DE MENÚ / PRODUCTOS (PyQt6)
# ============================================================

class MenuManagementDialog(QDialog):

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Gestión de Menú y Productos - RestaurantePOS")
        self.resize(750, 520)

        self.filtered_products = []
        self.init_ui()
        self.load_products()

    def init_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)

        top_layout = QHBoxLayout()

        self.txt_search = QLineEdit()
        self.txt_search.setPlaceholderText("🔍 Buscar producto por nombre...")
        self.txt_search.textChanged.connect(self.apply_filter)
        top_layout.addWidget(self.txt_search, stretch=2)

        self.cb_category = QComboBox()
        self.cb_category.currentIndexChanged.connect(self.apply_filter)
        top_layout.addWidget(self.cb_category, stretch=1)

        layout.addLayout(top_layout)

        self.table = QTableWidget()
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(["ID", "Categoría", "Nombre del Producto", "Precio ($)"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setStyleSheet("font-size: 13px; gridline-color: #E2E8F0;")
        layout.addWidget(self.table)

        buttons_layout = QHBoxLayout()

        btn_add = QPushButton("➕ Añadir Producto")
        btn_add.setStyleSheet("background-color: #16A34A; color: white; font-weight: bold; padding: 9px 15px;")
        btn_add.clicked.connect(self.dialog_add_product)

        btn_edit = QPushButton("✏️ Editar Seleccionado")
        btn_edit.setStyleSheet("background-color: #2563EB; color: white; font-weight: bold; padding: 9px 15px;")
        btn_edit.clicked.connect(self.dialog_edit_product)

        btn_delete = QPushButton("🗑️ Eliminar Seleccionado")
        btn_delete.setStyleSheet("background-color: #DC2626; color: white; font-weight: bold; padding: 9px 15px;")
        btn_delete.clicked.connect(self.delete_selected_product)

        buttons_layout.addWidget(btn_add)
        buttons_layout.addWidget(btn_edit)
        buttons_layout.addWidget(btn_delete)
        buttons_layout.addStretch()

        btn_close = QPushButton("Cerrar")
        btn_close.clicked.connect(self.accept)
        buttons_layout.addWidget(btn_close)

        layout.addLayout(buttons_layout)

    def load_products(self):
        with db_lock:
            cats = sorted(list(set(p["category"] for p in products_db if p.get("category"))))
            current_cat = self.cb_category.currentText()
            self.cb_category.blockSignals(True)
            self.cb_category.clear()
            self.cb_category.addItem("Todas las Categorías")
            self.cb_category.addItems(cats)
            if current_cat in cats:
                self.cb_category.setCurrentText(current_cat)
            self.cb_category.blockSignals(False)

        self.apply_filter()

    def apply_filter(self):
        query = self.txt_search.text().strip().lower()
        cat_filter = self.cb_category.currentText()

        with db_lock:
            self.filtered_products = []
            for p in products_db:
                matches_cat = (cat_filter == "Todas las Categorías" or p.get("category") == cat_filter)
                matches_query = (not query or query in p.get("name", "").lower() or query in p.get("category", "").lower())
                if matches_cat and matches_query:
                    self.filtered_products.append(p)

        self.table.setRowCount(len(self.filtered_products))
        for row, prod in enumerate(self.filtered_products):
            item_id = QTableWidgetItem(str(prod["id"]))
            item_id.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item_cat = QTableWidgetItem(prod.get("category", "General"))
            item_name = QTableWidgetItem(prod.get("name", ""))
            item_price = QTableWidgetItem(f"${prod.get('price', 0.0):.2f}")
            item_price.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)

            self.table.setItem(row, 0, item_id)
            self.table.setItem(row, 1, item_cat)
            self.table.setItem(row, 2, item_name)
            self.table.setItem(row, 3, item_price)

    def dialog_add_product(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Añadir Nuevo Producto al Menú")
        form = QFormLayout()

        cb_cat = QComboBox()
        cb_cat.setEditable(True)
        with db_lock:
            cats = sorted(list(set(p["category"] for p in products_db if p.get("category"))))
        cb_cat.addItems(cats or ["Comida", "Bebidas", "Postres", "General"])

        txt_name = QLineEdit()
        txt_name.setPlaceholderText("Ej. Tacos al Pastor, Jugo de Naranja")

        txt_price = QLineEdit()
        txt_price.setPlaceholderText("Ej. 45.00")

        form.addRow("Categoría:", cb_cat)
        form.addRow("Nombre del Producto:", txt_name)
        form.addRow("Precio ($):", txt_price)

        btn_save = QPushButton("Guardar Producto")
        btn_save.setStyleSheet("background-color: #16A34A; color: white; font-weight: bold; padding: 8px;")

        def save():
            cat = cb_cat.currentText().strip() or "General"
            name = txt_name.text().strip()
            try:
                price = float(txt_price.text().strip())
            except ValueError:
                QMessageBox.warning(dialog, "Error", "Ingresa un precio numérico válido.")
                return

            if not name or price <= 0:
                QMessageBox.warning(dialog, "Error", "El nombre es obligatorio y el precio debe ser mayor a 0.")
                return

            with db_lock:
                new_id = max([p["id"] for p in products_db], default=0) + 1
                products_db.append({
                    "id": new_id,
                    "category": cat,
                    "name": name,
                    "price": price
                })
                mark_database_changed()

            dialog.accept()
            self.load_products()

        btn_save.clicked.connect(save)
        layout = QVBoxLayout(dialog)
        layout.addLayout(form)
        layout.addWidget(btn_save)
        dialog.exec()

    def dialog_edit_product(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(self.filtered_products):
            QMessageBox.warning(self, "Atención", "Selecciona un producto de la tabla para editar.")
            return

        prod = self.filtered_products[row]

        dialog = QDialog(self)
        dialog.setWindowTitle(f"Editar Producto - {prod['name']}")
        form = QFormLayout()

        cb_cat = QComboBox()
        cb_cat.setEditable(True)
        with db_lock:
            cats = sorted(list(set(p["category"] for p in products_db if p.get("category"))))
        cb_cat.addItems(cats or ["General"])
        cb_cat.setCurrentText(prod.get("category", "General"))

        txt_name = QLineEdit(prod.get("name", ""))
        txt_price = QLineEdit(f"{prod.get('price', 0.0):.2f}")

        form.addRow("Categoría:", cb_cat)
        form.addRow("Nombre del Producto:", txt_name)
        form.addRow("Precio ($):", txt_price)

        btn_save = QPushButton("Actualizar Producto")
        btn_save.setStyleSheet("background-color: #2563EB; color: white; font-weight: bold; padding: 8px;")

        def save():
            cat = cb_cat.currentText().strip() or "General"
            name = txt_name.text().strip()
            try:
                price = float(txt_price.text().strip())
            except ValueError:
                QMessageBox.warning(dialog, "Error", "Ingresa un precio numérico válido.")
                return

            if not name or price <= 0:
                QMessageBox.warning(dialog, "Error", "El nombre es obligatorio y el precio debe ser mayor a 0.")
                return

            with db_lock:
                for p in products_db:
                    if p["id"] == prod["id"]:
                        p["category"] = cat
                        p["name"] = name
                        p["price"] = price
                        break
                mark_database_changed()

            dialog.accept()
            self.load_products()

        btn_save.clicked.connect(save)
        layout = QVBoxLayout(dialog)
        layout.addLayout(form)
        layout.addWidget(btn_save)
        dialog.exec()

    def delete_selected_product(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(self.filtered_products):
            QMessageBox.warning(self, "Atención", "Selecciona un producto para eliminar.")
            return

        prod = self.filtered_products[row]
        confirm = QMessageBox.question(
            self,
            "Confirmar Eliminación",
            f"¿Estás seguro de que deseas eliminar '{prod['name']}' del menú?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if confirm == QMessageBox.StandardButton.Yes:
            with db_lock:
                for i, p in enumerate(products_db):
                    if p["id"] == prod["id"]:
                        products_db.pop(i)
                        break
                mark_database_changed()
            self.load_products()


# ============================================================
# INTERFAZ GRÁFICA DE CAJA (PyQt6)
# ============================================================

class CashierWindow(QMainWindow):

    def __init__(self):
        super().__init__()

        self.local_ip = get_local_ip()

        self.setWindowTitle(
            f"RestaurantePOS - Central de Caja ({APP_VERSION}) | IP: {self.local_ip}:{SERVER_PORT}"
        )
        self.resize(1200, 740)

        self.selected_area_id = None
        self.selected_table_key = None

        self.local_orders_tracker = last_sync_version
        self.table_buttons = {}

        self.init_ui()

        self.timer = QTimer(self)
        self.timer.timeout.connect(self.check_for_updates)
        self.timer.start(1000)

    # --------------------------------------------------------
    # ACTUALIZACIÓN EN TIEMPO REAL
    # --------------------------------------------------------

    def check_for_updates(self):
        global last_sync_version

        if self.local_orders_tracker != last_sync_version:
            self.local_orders_tracker = last_sync_version
            try:
                winsound.MessageBeep(winsound.MB_ICONASTERISK)
            except Exception:
                pass

            self.rebuild_tabs()

        self.refresh_ui()

    # --------------------------------------------------------
    # CONSTRUCCIÓN DE UI
    # --------------------------------------------------------

    def init_ui(self):
        root_layout = QVBoxLayout()
        root_layout.setContentsMargins(12, 12, 12, 8)
        root_layout.setSpacing(10)

        # Banner superior de conexión Wi-Fi y accesos rápidos
        banner = QFrame()
        banner.setStyleSheet(
            "background-color: #1E293B; color: #F8FAFC; border-radius: 8px; padding: 6px 12px;"
        )
        banner_layout = QHBoxLayout(banner)
        banner_layout.setContentsMargins(8, 4, 8, 4)

        lbl_ip_info = QLabel(
            f"🟢 <b>Servidor Madre Activo:</b> <font color='#38BDF8'>http://{self.local_ip}:{SERVER_PORT}</font> "
            f"<i>(Ingresa esta IP en la App Android de los meseros)</i>"
        )
        lbl_ip_info.setStyleSheet("font-size: 13px;")

        btn_menu = QPushButton("🍔 Menú / Productos")
        btn_menu.setStyleSheet(
            "background-color: #F59E0B; color: #1E293B; font-weight: bold; "
            "padding: 5px 12px; border-radius: 4px; font-size: 12px;"
        )
        btn_menu.clicked.connect(self.open_menu_management)

        btn_open_folder = QPushButton("📂 Recibos PDF")
        btn_open_folder.setStyleSheet(
            "background-color: #334155; color: white; border: 1px solid #475569; "
            "padding: 5px 10px; border-radius: 4px; font-size: 12px;"
        )
        btn_open_folder.clicked.connect(self.open_receipts_folder)

        banner_layout.addWidget(lbl_ip_info)
        banner_layout.addStretch()
        banner_layout.addWidget(btn_menu)
        banner_layout.addWidget(btn_open_folder)

        root_layout.addWidget(banner)

        main_widget = QWidget()
        main_layout = QHBoxLayout(main_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(14)

        # ====================================================
        # PANEL IZQUIERDO: SALAS Y MESAS
        # ====================================================

        left_panel = QVBoxLayout()

        left_header = QLabel("<h3><b>📌 Control de Salas y Mesas</b></h3>")
        left_panel.addWidget(left_header)

        self.tab_widget = QTabWidget()
        self.tab_widget.setStyleSheet(
            "QTabBar::tab { font-weight: bold; font-size: 13px; padding: 8px 18px; }"
            "QTabWidget::pane { border: 1px solid #CBD5E1; border-radius: 6px; background: #F8FAFC; }"
        )
        left_panel.addWidget(self.tab_widget)

        room_buttons_layout = QHBoxLayout()

        btn_add_room = QPushButton("➕ Agregar Sala")
        btn_add_room.setStyleSheet("padding: 8px 12px; font-weight: bold;")
        btn_add_room.clicked.connect(self.dialog_add_room)

        btn_set_count = QPushButton("🔢 Mesas de Sala")
        btn_set_count.setStyleSheet("padding: 8px 12px; font-weight: bold;")
        btn_set_count.clicked.connect(self.prompt_table_count_current_room)

        btn_delete_room = QPushButton("🗑️ Eliminar Sala")
        btn_delete_room.setStyleSheet("padding: 8px 12px; font-weight: bold; color: #DC2626;")
        btn_delete_room.clicked.connect(self.delete_current_room)

        btn_move = QPushButton("🔄 Mover / Transferir")
        btn_move.setStyleSheet("padding: 8px 12px; font-weight: bold;")
        btn_move.clicked.connect(self.move_table_dialog)

        room_buttons_layout.addWidget(btn_add_room)
        room_buttons_layout.addWidget(btn_set_count)
        room_buttons_layout.addWidget(btn_delete_room)
        room_buttons_layout.addWidget(btn_move)

        left_panel.addLayout(room_buttons_layout)

        # ====================================================
        # PANEL DERECHO: DETALLE DE COMANDA Y COBRO
        # ====================================================

        right_panel = QVBoxLayout()

        header_right = QHBoxLayout()

        self.lbl_table_header = QLabel("<h3>Selecciona una mesa</h3>")
        self.lbl_table_header.setStyleSheet("font-weight: bold; color: #0F172A;")

        btn_tax = QPushButton("⚙️ IVA (%)")
        btn_tax.setFixedWidth(90)
        btn_tax.clicked.connect(self.config_tax_dialog)

        btn_about = QPushButton("ℹ️ Acerca de")
        btn_about.setFixedWidth(90)
        btn_about.clicked.connect(self.show_about_dialog)

        header_right.addWidget(self.lbl_table_header)
        header_right.addStretch()
        header_right.addWidget(btn_tax)
        header_right.addWidget(btn_about)

        right_panel.addLayout(header_right)

        self.list_items = QListWidget()
        self.list_items.setStyleSheet(
            "font-size: 13px; padding: 6px; border: 1px solid #CBD5E1; border-radius: 6px; background: white;"
        )
        right_panel.addWidget(self.list_items)

        # Resumen de totales
        totales_frame = QFrame()
        totales_frame.setStyleSheet(
            "background: #F1F5F9; border-radius: 6px; padding: 10px; border: 1px solid #E2E8F0;"
        )
        totales_layout = QVBoxLayout(totales_frame)
        totales_layout.setSpacing(4)

        self.lbl_subtotal = QLabel("Subtotal: $0.00")
        self.lbl_subtotal.setStyleSheet("font-size: 14px; color: #334155;")
        totales_layout.addWidget(self.lbl_subtotal)

        self.lbl_tax_info = QLabel(f"IVA ({IVA_PERCENTAGE:.1f}%): $0.00")
        self.lbl_tax_info.setStyleSheet("font-size: 14px; color: #334155;")
        totales_layout.addWidget(self.lbl_tax_info)

        self.lbl_total = QLabel("Total a Pagar: $0.00")
        self.lbl_total.setStyleSheet("font-size: 22px; font-weight: bold; color: #16A34A;")
        totales_layout.addWidget(self.lbl_total)

        right_panel.addWidget(totales_frame)

        btn_pay = QPushButton("💳 / 💵 COBRAR MESA")
        btn_pay.setStyleSheet(
            "background-color: #16A34A; color: white; font-weight: bold; "
            "padding: 14px; font-size: 16px; border-radius: 6px;"
        )
        btn_pay.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_pay.clicked.connect(self.open_payment_dialog)

        right_panel.addWidget(btn_pay)

        # Divisor vertical
        main_layout.addLayout(left_panel, stretch=3)

        line = QFrame()
        line.setFrameShape(QFrame.Shape.VLine)
        line.setStyleSheet("color: #CBD5E1;")
        main_layout.addWidget(line)

        main_layout.addLayout(right_panel, stretch=2)

        root_layout.addWidget(main_widget)

        lbl_credits = QLabel(
            f"Desarrollado por <b>{DEVELOPER_NAME}</b> | Soporte: {DEVELOPER_CONTACT} | Versión {APP_VERSION}"
        )
        lbl_credits.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lbl_credits.setStyleSheet("color: #64748B; font-size: 11px; margin-top: 4px;")
        root_layout.addWidget(lbl_credits)

        container = QWidget()
        container.setLayout(root_layout)
        self.setCentralWidget(container)

        self.rebuild_tabs()

    # --------------------------------------------------------
    # TABS Y BOTONES DE MESAS POR SALA (SIN DUPLICADOS)
    # --------------------------------------------------------

    def rebuild_tabs(self):
        with db_lock:
            normalize_rooms_db()
            current_tab_idx = max(0, self.tab_widget.currentIndex())
            self.tab_widget.clear()
            self.table_buttons.clear()

            for area_id, room_data in rooms_db.items():
                prefix = room_data.get("prefix", "M").strip()
                count = room_data.get("count", 10)

                scroll_widget = QWidget()
                grid = QGridLayout(scroll_widget)
                grid.setSpacing(10)
                grid.setContentsMargins(10, 10, 10, 10)

                row = 0
                col = 0

                # Iteramos estrictamente por número del 1 al count para asegurar unicidad
                for num in range(1, count + 1):
                    table_key = f"Mesa {num}"
                    t_val = room_data["mesas"].get(table_key, {
                        "number": num,
                        "camarero": "",
                        "items": [],
                        "total": 0.0
                    })

                    is_occ = len(t_val.get("items", [])) > 0
                    total_val = t_val.get("total", 0.0)

                    # Mostrar con el prefijo configurado de la sala (ej. br1, br2 o Mesa 1)
                    if prefix and prefix.upper() != "M":
                        display_label = f"{prefix}{num}"
                    else:
                        display_label = f"Mesa {num}"

                    btn = QPushButton()
                    btn.setFixedHeight(65)
                    btn.setCursor(Qt.CursorShape.PointingHandCursor)

                    if is_occ:
                        btn.setText(f"{display_label}\n${total_val:.2f}")
                        btn.setStyleSheet(
                            "background-color: #DC2626; color: white; font-weight: bold; "
                            "font-size: 13px; border-radius: 8px;"
                        )
                    else:
                        btn.setText(f"{display_label}\nLibre")
                        btn.setStyleSheet(
                            "background-color: #16A34A; color: white; font-weight: bold; "
                            "font-size: 13px; border-radius: 8px;"
                        )

                    btn.clicked.connect(
                        lambda _, a_id=area_id, t_k=table_key: self.select_table(a_id, t_k)
                    )

                    grid.addWidget(btn, row, col)
                    self.table_buttons[(area_id, table_key)] = btn

                    col += 1
                    if col > 3:
                        col = 0
                        row += 1

                scroll_area = QScrollArea()
                scroll_area.setWidgetResizable(True)
                scroll_area.setWidget(scroll_widget)

                tab_title = f"{room_data['name']} ({prefix})" if (prefix and prefix.upper() != "M") else room_data['name']
                self.tab_widget.addTab(scroll_area, tab_title)

            if current_tab_idx < self.tab_widget.count():
                self.tab_widget.setCurrentIndex(current_tab_idx)

    # --------------------------------------------------------
    # GESTIÓN DE SALAS (EXCLUSIVA DE PC)
    # --------------------------------------------------------

    def dialog_add_room(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Agregar Nueva Sala")
        form = QFormLayout()

        txt_name = QLineEdit()
        txt_name.setPlaceholderText("Ej. Terraza, Barra, VIP")

        txt_prefix = QLineEdit()
        txt_prefix.setPlaceholderText("Ej. T, br, V")

        spin_count = QLineEdit("10")

        form.addRow("Nombre de Sala:", txt_name)
        form.addRow("Prefijo de Mesas:", txt_prefix)
        form.addRow("Cantidad de Mesas:", spin_count)

        btn_save = QPushButton("Guardar Sala")
        btn_save.setStyleSheet("background-color: #16A34A; color: white; font-weight: bold; padding: 8px;")

        def save():
            name = txt_name.text().strip()
            prefix = txt_prefix.text().strip() or "M"

            try:
                count = int(spin_count.text().strip())
            except ValueError:
                count = 10

            count = max(1, count)

            if not name:
                QMessageBox.warning(dialog, "Atención", "Ingresa el nombre de la sala.")
                return

            with db_lock:
                for existing_id, existing_data in rooms_db.items():
                    if existing_data["name"].strip().lower() == name.lower():
                        existing_data["prefix"] = prefix
                        existing_data["count"] = count
                        mark_database_changed()
                        dialog.accept()
                        self.rebuild_tabs()
                        return

                new_id = max(rooms_db.keys(), default=0) + 1
                rooms_db[new_id] = {
                    "id": new_id,
                    "name": name,
                    "prefix": prefix,
                    "count": count,
                    "mesas": {
                        f"Mesa {i}": {
                            "number": i,
                            "camarero": "",
                            "items": [],
                            "total": 0.0
                        }
                        for i in range(1, count + 1)
                    }
                }
                mark_database_changed()

            dialog.accept()
            self.rebuild_tabs()

        btn_save.clicked.connect(save)

        layout = QVBoxLayout()
        layout.addLayout(form)
        layout.addWidget(btn_save)
        dialog.setLayout(layout)
        dialog.exec()

    def prompt_table_count_current_room(self):
        curr_idx = self.tab_widget.currentIndex()
        if curr_idx < 0:
            return

        with db_lock:
            area_ids = list(rooms_db.keys())
            if curr_idx >= len(area_ids):
                return

            area_id = area_ids[curr_idx]
            room_name = rooms_db[area_id]["name"]
            current_val = rooms_db[area_id].get("count", 10)

        num, ok = QInputDialog.getInt(
            self,
            "Configurar Mesas",
            f"Número de mesas para la sala '{room_name}':",
            value=max(1, current_val),
            min=1,
            max=100
        )

        if ok:
            update_room_tables_count(area_id, num)
            self.rebuild_tabs()

    def delete_current_room(self):
        curr_idx = self.tab_widget.currentIndex()
        if curr_idx < 0:
            return

        with db_lock:
            area_ids = list(rooms_db.keys())
            if curr_idx >= len(area_ids):
                return

            if len(area_ids) <= 1:
                QMessageBox.warning(self, "Atención", "Debe existir al menos una sala en el sistema.")
                return

            area_id = area_ids[curr_idx]
            room_name = rooms_db[area_id]["name"]

        confirm = QMessageBox.question(
            self,
            "Eliminar Sala",
            f"¿Estás seguro de que deseas eliminar la sala '{room_name}' y todas sus mesas?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )

        if confirm == QMessageBox.StandardButton.Yes:
            with db_lock:
                if area_id in rooms_db:
                    del rooms_db[area_id]
                    if self.selected_area_id == area_id:
                        self.selected_area_id = None
                        self.selected_table_key = None
                    mark_database_changed()
            self.rebuild_tabs()
            self.refresh_ui()

    # --------------------------------------------------------
    # CONFIGURAR IVA
    # --------------------------------------------------------

    def config_tax_dialog(self):
        global IVA_PERCENTAGE

        val, ok = QInputDialog.getDouble(
            self,
            "Configurar IVA",
            "Porcentaje de IVA (%):",
            value=IVA_PERCENTAGE,
            min=0.0,
            max=100.0,
            decimals=2
        )

        if ok:
            IVA_PERCENTAGE = val
            self.refresh_ui()

    # --------------------------------------------------------
    # SELECCIÓN Y REFRESCO DE MESAS
    # --------------------------------------------------------

    def select_table(self, area_id: int, table_name: str):
        self.selected_area_id = area_id
        self.selected_table_key = table_name
        self.refresh_ui()

    def refresh_ui(self):
        with db_lock:
            for area_id, room_data in rooms_db.items():
                prefix = room_data.get("prefix", "M").strip()
                for name, data in room_data["mesas"].items():
                    btn_key = (area_id, name)
                    if btn_key in self.table_buttons:
                        btn = self.table_buttons[btn_key]
                        num = data.get("number", 1)
                        is_occ = len(data.get("items", [])) > 0
                        tot = data.get("total", 0.0)

                        if prefix and prefix.upper() != "M":
                            display_label = f"{prefix}{num}"
                        else:
                            display_label = f"Mesa {num}"

                        if is_occ:
                            btn.setText(f"{display_label}\n${tot:.2f}")
                            btn.setStyleSheet(
                                "background-color: #DC2626; color: white; font-weight: bold; "
                                "font-size: 13px; border-radius: 8px;"
                            )
                        else:
                            btn.setText(f"{display_label}\nLibre")
                            btn.setStyleSheet(
                                "background-color: #16A34A; color: white; font-weight: bold; "
                                "font-size: 13px; border-radius: 8px;"
                            )

            data = None
            room_name = ""
            display_header_name = ""
            if self.selected_area_id and self.selected_area_id in rooms_db and self.selected_table_key:
                room_data = rooms_db[self.selected_area_id]
                room_name = room_data["name"]
                prefix = room_data.get("prefix", "M").strip()
                data = room_data["mesas"].get(self.selected_table_key)
                num = data.get("number", 1) if data else 1
                if prefix and prefix.upper() != "M":
                    display_header_name = f"{prefix}{num}"
                else:
                    display_header_name = f"Mesa {num}"

        if data:
            camarero_txt = data.get("camarero") or "Caja"
            self.lbl_table_header.setText(
                f"<h3>Detalle: <b>{display_header_name} ({room_name})</b> <font size='3' color='#64748B'>(Atiende: {camarero_txt})</font></h3>"
            )

            self.list_items.clear()
            for item in data.get("items", []):
                subtotal = item["cantidad"] * item["precio"]
                self.list_items.addItem(f"• {item['nombre']}  x{item['cantidad']}  —  ${subtotal:.2f}")

            subtotal_base = data.get("total", 0.0)
            monto_iva = subtotal_base * (IVA_PERCENTAGE / 100.0)
            total_final = subtotal_base + monto_iva

            self.lbl_subtotal.setText(f"Subtotal: ${subtotal_base:.2f}")
            self.lbl_tax_info.setText(f"IVA ({IVA_PERCENTAGE:.1f}%): ${monto_iva:.2f}")
            self.lbl_total.setText(f"Total a Pagar: ${total_final:.2f}")
        else:
            self.lbl_table_header.setText("<h3>Selecciona una mesa</h3>")
            self.list_items.clear()
            self.lbl_subtotal.setText("Subtotal: $0.00")
            self.lbl_tax_info.setText(f"IVA ({IVA_PERCENTAGE:.1f}%): $0.00")
            self.lbl_total.setText("Total a Pagar: $0.00")

    # --------------------------------------------------------
    # TRASLADO / MOVER MESA
    # --------------------------------------------------------

    def move_table_dialog(self):
        if not self.selected_area_id or not self.selected_table_key:
            QMessageBox.warning(self, "Atención", "Selecciona una mesa ocupada para mover.")
            return

        with db_lock:
            room = rooms_db.get(self.selected_area_id)
            src_data = room["mesas"].get(self.selected_table_key) if room else None

        if not src_data or len(src_data.get("items", [])) == 0:
            QMessageBox.warning(self, "Atención", "La mesa seleccionada no contiene consumos activos.")
            return

        dialog = QDialog(self)
        dialog.setWindowTitle("Mover / Transferir Mesa")

        layout = QVBoxLayout()
        layout.addWidget(QLabel(f"Mover consumos de <b>{self.selected_table_key} ({room['name']})</b> hacia:"))

        cb_target = QComboBox()
        free_tables = []
        target_map = {}

        with db_lock:
            for r_id, r_data in rooms_db.items():
                pfx = r_data.get("prefix", "M").strip()
                for t_key, t_val in r_data["mesas"].items():
                    if not (r_id == self.selected_area_id and t_key == self.selected_table_key) and len(t_val.get("items", [])) == 0:
                        nm = t_val.get("number", 1)
                        lbl = f"{pfx}{nm} ({r_data['name']})" if (pfx and pfx.upper() != "M") else f"Mesa {nm} ({r_data['name']})"
                        free_tables.append(lbl)
                        target_map[lbl] = (r_id, t_key)

        if not free_tables:
            QMessageBox.warning(self, "Atención", "No hay mesas libres disponibles.")
            return

        cb_target.addItems(free_tables)
        layout.addWidget(cb_target)

        btn_confirm = QPushButton("Confirmar Traslado")
        btn_confirm.setStyleSheet("background-color: #2563EB; color: white; font-weight: bold; padding: 8px;")
        btn_confirm.clicked.connect(lambda: self.execute_move(target_map[cb_target.currentText()], dialog))
        layout.addWidget(btn_confirm)

        dialog.setLayout(layout)
        dialog.exec()

    def execute_move(self, target_coords: tuple, dialog: QDialog):
        target_area_id, target_table_key = target_coords

        with db_lock:
            src_room = rooms_db.get(self.selected_area_id)
            target_room = rooms_db.get(target_area_id)

            if not src_room or not target_room:
                QMessageBox.critical(self, "Error", "Error al localizar salas.")
                return

            source = src_room["mesas"].get(self.selected_table_key)
            target = target_room["mesas"].get(target_table_key)

            if not source or not target:
                QMessageBox.critical(self, "Error", "Mesa destino no encontrada.")
                return

            if len(target.get("items", [])) > 0:
                QMessageBox.critical(self, "Error", "La mesa destino ya está ocupada.")
                return

            target["items"] = copy.deepcopy(source.get("items", []))
            target["camarero"] = source.get("camarero", "")
            update_occupied_status(target)

            source["items"] = []
            source["camarero"] = ""
            source["total"] = 0.0

            self.selected_area_id = target_area_id
            self.selected_table_key = target_table_key
            mark_database_changed()

        dialog.accept()
        self.refresh_ui()

    # --------------------------------------------------------
    # COBRO Y FACTURACIÓN
    # --------------------------------------------------------

    def open_payment_dialog(self):
        with db_lock:
            room = rooms_db.get(self.selected_area_id) if self.selected_area_id else None
            mesa_data = room["mesas"].get(self.selected_table_key) if room else None

        if not mesa_data or len(mesa_data.get("items", [])) == 0:
            QMessageBox.warning(self, "Atención", "Selecciona una mesa con consumos para cobrar.")
            return

        prefix = room.get("prefix", "M").strip()
        num = mesa_data.get("number", 1)
        display_name = f"{prefix}{num}" if (prefix and prefix.upper() != "M") else f"Mesa {num}"

        subtotal_base = mesa_data.get("total", 0.0)
        monto_iva = subtotal_base * (IVA_PERCENTAGE / 100.0)
        total_due = subtotal_base + monto_iva

        dialog = QDialog(self)
        dialog.setWindowTitle(f"Cobrar {display_name} ({room['name']}) - Total: ${total_due:.2f}")
        dialog.resize(380, 280)

        layout = QVBoxLayout()
        layout.setSpacing(10)

        layout.addWidget(QLabel(
            f"<b>Subtotal:</b> ${subtotal_base:.2f} | <b>IVA ({IVA_PERCENTAGE:.1f}%):</b> ${monto_iva:.2f}<br>"
            f"<h3><b>TOTAL A PAGAR: ${total_due:.2f}</b></h3>"
        ))

        layout.addWidget(QLabel("<b>Monto en Efectivo ($):</b>"))
        txt_cash = QLineEdit("0.00")
        layout.addWidget(txt_cash)

        layout.addWidget(QLabel("<b>Monto en Tarjeta ($):</b>"))
        txt_card = QLineEdit(f"{total_due:.2f}")
        layout.addWidget(txt_card)

        lbl_change = QLabel("Cambio / Vuelto: $0.00")
        lbl_change.setStyleSheet("font-size: 15px; font-weight: bold; color: #16A34A;")
        layout.addWidget(lbl_change)

        def calculate_change():
            try:
                cash = float(txt_cash.text()) if txt_cash.text() else 0.0
                card = float(txt_card.text()) if txt_card.text() else 0.0
                paid = cash + card
                diff = paid - total_due
                if diff >= -0.01:
                    lbl_change.setStyleSheet("font-size: 15px; font-weight: bold; color: #16A34A;")
                    lbl_change.setText(f"Cambio / Vuelto: ${max(0.0, diff):.2f}")
                else:
                    lbl_change.setStyleSheet("font-size: 15px; font-weight: bold; color: #DC2626;")
                    lbl_change.setText(f"Falta: ${abs(diff):.2f}")
            except ValueError:
                pass

        txt_cash.textChanged.connect(calculate_change)
        txt_card.textChanged.connect(calculate_change)

        btn_finish = QPushButton("🖨️ Confirmar Pago y Generar Recibo PDF")
        btn_finish.setStyleSheet(
            "background-color: #16A34A; color: white; font-weight: bold; padding: 12px; font-size: 14px;"
        )
        btn_finish.clicked.connect(
            lambda: self.process_payment(
                dialog, txt_cash, txt_card, mesa_data, subtotal_base, monto_iva, total_due, room["name"], display_name
            )
        )
        layout.addWidget(btn_finish)

        dialog.setLayout(layout)
        dialog.exec()

    def process_payment(
        self,
        dialog: QDialog,
        txt_cash: QLineEdit,
        txt_card: QLineEdit,
        mesa_data: dict,
        subtotal: float,
        monto_iva: float,
        total_due: float,
        room_name: str,
        display_name: str
    ):
        try:
            cash = float(txt_cash.text()) if txt_cash.text() else 0.0
            card = float(txt_card.text()) if txt_card.text() else 0.0
        except ValueError:
            cash = 0.0
            card = total_due

        change = (cash + card) - total_due
        if change < -0.01:
            QMessageBox.warning(self, "Pago insuficiente", f"Falta pagar ${abs(change):.2f}")
            return

        pdf_path = ""
        try:
            full_table_desc = f"{display_name} ({room_name})"
            pdf_path = generar_recibo_pdf(
                nombre_mesa=full_table_desc,
                camarero=mesa_data.get("camarero", ""),
                items=copy.deepcopy(mesa_data.get("items", [])),
                subtotal=subtotal,
                iva_monto=monto_iva,
                total=total_due,
                efectivo=cash,
                tarjeta=card,
                cambio=max(0.0, change)
            )
        except Exception as e:
            print(f"Error generando PDF: {e}")

        with db_lock:
            mesa_data["items"] = []
            mesa_data["camarero"] = ""
            mesa_data["total"] = 0.0
            mark_database_changed()

        QMessageBox.information(
            self,
            "Cobro Exitoso",
            f"El pago ha sido registrado correctamente.\nRecibo guardado en:\n{pdf_path or CARPETA_RECIBOS}"
        )

        dialog.accept()
        self.refresh_ui()

    # --------------------------------------------------------
    # ACCIONES ADICIONALES
    # --------------------------------------------------------

    def open_menu_management(self):
        dialog = MenuManagementDialog(self)
        dialog.exec()

    def open_receipts_folder(self):
        os.makedirs(CARPETA_RECIBOS, exist_ok=True)
        try:
            if sys.platform == "win32":
                os.startfile(os.path.abspath(CARPETA_RECIBOS))
            elif sys.platform == "darwin":
                subprocess.Popen(["open", os.path.abspath(CARPETA_RECIBOS)])
            else:
                subprocess.Popen(["xdg-open", os.path.abspath(CARPETA_RECIBOS)])
        except Exception as e:
            QMessageBox.warning(self, "Error", f"No se pudo abrir la carpeta: {e}")

    def show_about_dialog(self):
        QMessageBox.about(
            self,
            "Acerca del Sistema",
            f"<h2>RestaurantePOS - Central de Caja</h2>"
            f"<p><b>Versión:</b> {APP_VERSION}</p>"
            f"<p><b>Desarrollador:</b> {DEVELOPER_NAME}</p>"
            f"<p><b>Contacto / Soporte:</b> {DEVELOPER_CONTACT}</p>"
            f"<p><b>IP de Sincronización:</b> {self.local_ip}:{SERVER_PORT}</p>"
        )


# ============================================================
# PUNTO DE ENTRADA PRINCIPAL
# ============================================================

if __name__ == "__main__":
    threading.Thread(target=run_server, daemon=True).start()

    app = QApplication(sys.argv)
    window = CashierWindow()
    window.show()
    sys.exit(app.exec())
