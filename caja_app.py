import sys
import threading
import winsound
import os
import copy
import json
import socket
import subprocess
import shutil
import time

if sys.stdout is None:
    sys.stdout = open(os.devnull, "w", encoding="utf-8")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w", encoding="utf-8")

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
    QAbstractItemView,
    QFileDialog
)

from PyQt6.QtCore import Qt, QTimer, QSize
from PyQt6.QtGui import QPixmap, QIcon

import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# ============================================================
# DATOS DEL DESARROLLADOR Y CONFIGURACIÓN
# ============================================================

DEVELOPER_NAME = "Sureshum"
DEVELOPER_CONTACT = "ssshum25ssshum25@gmail.com"

APP_VERSION = "v1.8.3"

if getattr(sys, 'frozen', False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

CARPETA_RECIBOS = os.path.join(BASE_DIR, "recibos")
CARPETA_IMAGENES = os.path.join(BASE_DIR, "imagenes_productos")
DATA_FILE = os.path.join(BASE_DIR, "pos_database.json")

# Escucha en 0.0.0.0 para que el teléfono pueda conectar por la red local.
# IMPORTANTE (seguridad): NO abras el puerto SERVER_PORT en el router/firewall
# hacia Internet; esta API no tiene autenticación y solo debe ser accesible
# dentro de tu red local. Restringe el firewall a tu rango LAN.
SERVER_HOST = "0.0.0.0"
SERVER_PORT = 5000

IVA_PERCENTAGE = 16.0

os.makedirs(CARPETA_RECIBOS, exist_ok=True)
os.makedirs(CARPETA_IMAGENES, exist_ok=True)

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
    imageUri: Optional[str] = None


class SaleItemPayload(BaseModel):
    nombre: str
    cantidad: Optional[int] = 1
    precio: float = 0.0


class SalePayload(BaseModel):
    mesa: str
    sala: Optional[str] = ""
    camarero: Optional[str] = ""
    items: List[SaleItemPayload] = []
    efectivo: float = 0.0
    tarjeta: float = 0.0
    areaId: Optional[int] = None


# ============================================================
# BASE DE DATOS MADRE (SERVIDOR PRINCIPAL)
# ============================================================

DEFAULT_ROOMS_DB: Dict[int, dict] = {
    1: {
        "id": 1,
        "name": "sala",
        "prefix": "mesa",
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
    },
    2: {
        "id": 2,
        "name": "Barra",
        "prefix": "br",
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
    {"id": 1, "category": "Comida", "name": "Hamburguesa Clásica", "price": 85.0, "imageUri": ""},
    {"id": 2, "category": "Comida", "name": "Pizza Pepperoni", "price": 120.0, "imageUri": ""},
    {"id": 3, "category": "Comida", "name": "Papas Fritas", "price": 45.0, "imageUri": ""},
    {"id": 4, "category": "Comida", "name": "Tacos de Bistec (3pz)", "price": 60.0, "imageUri": ""},
    {"id": 5, "category": "Bebidas", "name": "Refresco Coca-Cola 600ml", "price": 25.0, "imageUri": ""},
    {"id": 6, "category": "Bebidas", "name": "Agua Fresca Natural", "price": 20.0, "imageUri": ""},
    {"id": 7, "category": "Bebidas", "name": "Cerveza Corona", "price": 35.0, "imageUri": ""},
    {"id": 8, "category": "Postres", "name": "Rebanada de Pastel", "price": 40.0, "imageUri": ""}
]

rooms_db: Dict[int, dict] = {}
products_db: List[dict] = []
daily_sales_db: List[dict] = []
last_sync_version = 0
last_day_reset_ts = 0.0


def normalize_rooms_db():
    """Garantiza que cada sala tenga sus mesas ordenadas 1..count sin perder mesas ocupadas."""
    with db_lock:
        for area_id, room in list(rooms_db.items()):
            base_count = max(1, int(room.get("count", 10)))
            prefix = room.get("prefix", "M").strip()
            room["prefix"] = prefix if prefix else "M"

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
                    if len(val.get("items", [])) > len(tables_by_number[num].get("items", [])):
                        val["number"] = num
                        tables_by_number[num] = val

            max_num = max([base_count] + list(tables_by_number.keys()))
            room["count"] = max_num

            clean_mesas = {}
            for i in range(1, max_num + 1):
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
                "products": products_db,
                "daily_sales": daily_sales_db,
                "iva_percentage": IVA_PERCENTAGE,
                "day_reset_ts": last_day_reset_ts
            }
            with open(DATA_FILE, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"Error al guardar la base de datos: {e}")


def load_database():
    global rooms_db, products_db, daily_sales_db, IVA_PERCENTAGE, last_day_reset_ts
    with db_lock:
        if os.path.exists(DATA_FILE):
            try:
                with open(DATA_FILE, "r", encoding="utf-8") as f:
                    raw_data = json.load(f)

                    if "rooms" in raw_data:
                        rooms_db = {int(k): v for k, v in raw_data["rooms"].items()}
                        products_db = raw_data.get("products", copy.deepcopy(DEFAULT_PRODUCTS))
                        daily_sales_db = raw_data.get("daily_sales", [])
                        IVA_PERCENTAGE = raw_data.get("iva_percentage", 16.0)
                        last_day_reset_ts = float(raw_data.get("day_reset_ts", 0.0))
                    else:
                        rooms_db = {int(k): v for k, v in raw_data.items()}
                        products_db = copy.deepcopy(DEFAULT_PRODUCTS)
                        daily_sales_db = []

                    normalize_rooms_db()
                    return
            except Exception as e:
                print(f"Error al cargar base de datos: {e}")

        rooms_db = copy.deepcopy(DEFAULT_ROOMS_DB)
        products_db = copy.deepcopy(DEFAULT_PRODUCTS)
        daily_sales_db = []
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
    if not table_identifier:
        return None, None, None, None

    with db_lock:
        digits = ''.join(filter(str.isdigit, str(table_identifier)))
        target_num = int(digits) if digits else 1

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


def generar_reporte_cierre_dia_pdf(sales_list: list) -> str:
    fecha_hoy = datetime.now().strftime("%Y-%m-%d")
    nombre_archivo = f"Cierre_Caja_{fecha_hoy}_{datetime.now().strftime('%H%M%S')}.pdf"
    ruta_completa = os.path.join(CARPETA_RECIBOS, nombre_archivo)

    from reportlab.lib.pagesizes import letter
    from reportlab.pdfgen import canvas

    c = canvas.Canvas(ruta_completa, pagesize=letter)
    y = 750

    c.setFont("Helvetica-Bold", 16)
    c.drawCentredString(306, y, "--- REPORTE DE CIERRE DE CAJA / DÍA ---")
    y -= 30

    total_vendido = sum(s.get("total", 0.0) for s in sales_list)
    total_efectivo = sum(s.get("efectivo", 0.0) for s in sales_list)
    total_tarjeta = sum(s.get("tarjeta", 0.0) for s in sales_list)
    total_subtotal = sum(s.get("subtotal", 0.0) for s in sales_list)
    total_iva = sum(s.get("iva", 0.0) for s in sales_list)
    total_cuentas = len(sales_list)

    product_counts = {}
    for s in sales_list:
        for it in s.get("items", []):
            p_name = it.get("nombre", "Producto")
            p_cant = it.get("cantidad", 1)
            p_precio = it.get("precio", 0.0)
            if p_name not in product_counts:
                product_counts[p_name] = {"cantidad": 0, "total": 0.0}
            product_counts[p_name]["cantidad"] += p_cant
            product_counts[p_name]["total"] += (p_cant * p_precio)

    c.setFont("Helvetica", 11)
    c.drawString(80, y, f"Fecha de Cierre: {datetime.now().strftime('%d/%m/%Y %H:%M:%S')}")
    c.drawString(340, y, f"Cuentas Cobradas: {total_cuentas}")
    y -= 18
    c.drawString(80, y, f"Efectivo: ${total_efectivo:.2f}")
    c.drawString(220, y, f"Tarjeta: ${total_tarjeta:.2f}")
    c.drawString(340, y, f"Subtotal Base: ${total_subtotal:.2f}")
    y -= 18
    c.setFont("Helvetica-Bold", 13)
    c.drawString(80, y, f"GRAN TOTAL DEL DÍA: ${total_vendido:.2f}")
    c.drawString(340, y, f"Total IVA ({IVA_PERCENTAGE:.1f}%): ${total_iva:.2f}")
    y -= 22

    c.setLineWidth(1)
    c.line(80, y, 532, y)
    y -= 18

    c.setFont("Helvetica-Bold", 12)
    c.drawString(80, y, "Platillo / Producto")
    c.drawString(310, y, "Cant. Total")
    c.drawString(440, y, "Total Recaudado")
    y -= 12
    c.line(80, y, 532, y)
    y -= 18

    c.setFont("Helvetica", 10)
    for p_name, p_data in sorted(product_counts.items(), key=lambda x: x[1]["cantidad"], reverse=True):
        c.drawString(80, y, str(p_name)[:30])
        c.drawString(320, y, str(p_data["cantidad"]))
        c.drawString(440, y, f"${p_data['total']:.2f}")
        y -= 16

        if y < 100:
            c.showPage()
            y = 750
            c.setFont("Helvetica", 10)

    y -= 10
    c.line(80, y, 532, y)
    y -= 22

    c.setFont("Helvetica-Bold", 11)
    c.drawString(80, y, "Historial de Cuentas Cobradas:")
    y -= 16
    c.setFont("Helvetica", 9)
    for s in sales_list:
        hora = s.get('fecha_hora', '')[-8:] if len(s.get('fecha_hora', '')) >= 8 else ''
        c.drawString(80, y, f"• [{hora}] {s.get('mesa', '')} ({s.get('sala', '')}) - Total: ${s.get('total', 0.0):.2f} (Efec: ${s.get('efectivo', 0.0):.2f} | Tarj: ${s.get('tarjeta', 0.0):.2f}) - Atendió: {s.get('camarero', '')}")
        y -= 14
        if y < 100:
            c.showPage()
            y = 750
            c.setFont("Helvetica", 9)

    c.setFont("Helvetica-Oblique", 8)
    c.drawCentredString(306, 30, f"Reporte de Cierre generado por RestaurantePOS - {DEVELOPER_NAME}")
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
    allow_origins=[],
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Content-Type", "Authorization"],
)

# Servir archivos estáticos de imágenes para que los teléfonos Android las carguen
api.mount("/images", StaticFiles(directory=CARPETA_IMAGENES), name="images")


@api.get("/")
def health_check():
    return {
        "status": "ok",
        "app": "RestaurantePOS",
        "version": APP_VERSION,
        "sync_version": last_sync_version,
        "local_ip": get_local_ip()
    }


@api.get("/sync-fast")
def sync_fast(areaId: Optional[int] = None, version: int = 0):
    global last_sync_version
    with db_lock:
        needs_full = (version != last_sync_version)
        return {
            "version": last_sync_version,
            "has_changed": needs_full,
            "areas": serialize_areas() if needs_full else [],
            "products": products_db if needs_full else [],
            "tables": serialize_tables_dict(areaId),
            "daily_sales": [dict(s) for s in daily_sales_db],
            "day_reset": last_day_reset_ts
        }


@api.get("/areas")
def get_areas():
    return serialize_areas()


@api.post("/areas")
def insert_area(payload: AreaSyncPayload):
    with db_lock:
        clean_name = payload.name.strip()
        clean_prefix = payload.prefix.strip() or "M"

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


@api.post("/sale")
def register_sale(payload: SalePayload):
    global last_sync_version
    with db_lock:
        items = [
            {
                "nombre": item.nombre.strip(),
                "cantidad": max(1, int(item.cantidad or 1)),
                "precio": float(item.precio or 0.0)
            }
            for item in payload.items
            if item.nombre and item.nombre.strip() and float(item.precio or 0.0) > 0
        ]

        subtotal = sum(i["cantidad"] * i["precio"] for i in items)
        monto_iva = subtotal * (IVA_PERCENTAGE / 100.0)
        total_due = subtotal + monto_iva
        efectivo = float(payload.efectivo or 0.0)
        tarjeta = float(payload.tarjeta or 0.0)
        cambio = max(0.0, (efectivo + tarjeta) - total_due)

        sala = (payload.sala or "").strip()
        if not sala and payload.areaId is not None and payload.areaId in rooms_db:
            sala = rooms_db[payload.areaId]["name"]

        sale_record = {
            "id": int(time.time() * 1000),
            "fecha_hora": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "mesa": (payload.mesa or "Mesa").strip(),
            "sala": sala,
            "camarero": (payload.camarero or "").strip() or "Caja",
            "items": items,
            "subtotal": subtotal,
            "iva": monto_iva,
            "total": total_due,
            "efectivo": efectivo,
            "tarjeta": tarjeta,
            "cambio": cambio
        }
        daily_sales_db.append(sale_record)
        mark_database_changed()
        save_database()

    return {
        "status": "ok",
        "message": "Venta registrada",
        "sync_version": last_sync_version
    }


@api.post("/reset-day")
def reset_day_endpoint():
    global last_day_reset_ts
    with db_lock:
        daily_sales_db.clear()
        last_day_reset_ts = time.time()
        save_database()
    return {
        "status": "ok",
        "message": "Día finalizado",
        "day_reset": last_day_reset_ts
    }


@api.get("/ping")
def ping_endpoint():
    return {
        "status": "ok",
        "message": "Servidor Madre Activo",
        "version": last_sync_version,
        "time": time.time()
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
            "price": float(payload.price),
            "imageUri": payload.imageUri or ""
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
                if payload.imageUri is not None:
                    p["imageUri"] = payload.imageUri
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
# DIÁLOGO DE CIERRE DE DÍA / REPORTES (PyQt6)
# ============================================================

class CierreDiaDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("📊 Reporte de Cierre de Caja / Fin del Día")
        self.resize(640, 560)
        self.init_ui()

    def init_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)

        with db_lock:
            sales = list(daily_sales_db)

        total_vendido = sum(s.get("total", 0.0) for s in sales)
        total_efectivo = sum(s.get("efectivo", 0.0) for s in sales)
        total_tarjeta = sum(s.get("tarjeta", 0.0) for s in sales)
        total_subtotal = sum(s.get("subtotal", 0.0) for s in sales)
        total_iva = sum(s.get("iva", 0.0) for s in sales)
        total_cuentas = len(sales)

        # Tarjetas de resumen
        cards_frame = QFrame()
        cards_frame.setStyleSheet("background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 10px;")
        cards_layout = QGridLayout(cards_frame)
        cards_layout.setSpacing(12)

        lbl_tot = QLabel(f"💰 <b>Total Facturado:</b><br><font size='5' color='#16A34A'><b>${total_vendido:.2f}</b></font>")
        lbl_efectivo = QLabel(f"💵 <b>Total Efectivo:</b><br><font size='4' color='#0284C7'><b>${total_efectivo:.2f}</b></font>")
        lbl_tarjeta = QLabel(f"💳 <b>Total Tarjeta:</b><br><font size='4' color='#7C3AED'><b>${total_tarjeta:.2f}</b></font>")
        lbl_cuentas = QLabel(f"🧾 <b>Cuentas Cobradas:</b><br><font size='4' color='#334155'><b>{total_cuentas}</b></font>")

        cards_layout.addWidget(lbl_tot, 0, 0)
        cards_layout.addWidget(lbl_efectivo, 0, 1)
        cards_layout.addWidget(lbl_tarjeta, 1, 0)
        cards_layout.addWidget(lbl_cuentas, 1, 1)

        layout.addWidget(cards_frame)

        # Tabla de desglose de productos vendidos
        layout.addWidget(QLabel("<b>🍽️ Desglose de Productos Vendidos Hoy:</b>"))
        table = QTableWidget()
        table.setColumnCount(3)
        table.setHorizontalHeaderLabels(["Producto", "Cant. Total", "Total Recaudado ($)"])
        table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)

        product_counts = {}
        for s in sales:
            for it in s.get("items", []):
                p_name = it.get("nombre", "Producto")
                p_cant = it.get("cantidad", 1)
                p_precio = it.get("precio", 0.0)
                if p_name not in product_counts:
                    product_counts[p_name] = {"cantidad": 0, "total": 0.0}
                product_counts[p_name]["cantidad"] += p_cant
                product_counts[p_name]["total"] += (p_cant * p_precio)

        table.setRowCount(len(product_counts))
        for row_idx, (p_name, p_data) in enumerate(sorted(product_counts.items(), key=lambda x: x[1]["cantidad"], reverse=True)):
            table.setItem(row_idx, 0, QTableWidgetItem(str(p_name)))
            item_cant = QTableWidgetItem(str(p_data["cantidad"]))
            item_cant.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            table.setItem(row_idx, 1, item_cant)
            item_tot = QTableWidgetItem(f"${p_data['total']:.2f}")
            item_tot.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            table.setItem(row_idx, 2, item_tot)

        layout.addWidget(table)

        # Botones de Acción
        btn_layout = QHBoxLayout()
        btn_layout.setSpacing(10)

        btn_pdf = QPushButton("📄 Generar Reporte PDF")
        btn_pdf.setStyleSheet("background-color: #0284C7; color: white; font-weight: bold; padding: 10px; border-radius: 6px; font-size: 13px;")
        btn_pdf.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_pdf.clicked.connect(self.export_pdf)

        btn_reset = QPushButton("🔄 Finalizar Día / Reiniciar Caja")
        btn_reset.setStyleSheet("background-color: #DC2626; color: white; font-weight: bold; padding: 10px; border-radius: 6px; font-size: 13px;")
        btn_reset.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_reset.clicked.connect(self.reset_day)

        btn_close = QPushButton("Cerrar")
        btn_close.setStyleSheet("background-color: #64748B; color: white; font-weight: bold; padding: 10px; border-radius: 6px; font-size: 13px;")
        btn_close.clicked.connect(self.accept)

        btn_layout.addWidget(btn_pdf)
        btn_layout.addWidget(btn_reset)
        btn_layout.addWidget(btn_close)

        layout.addLayout(btn_layout)

    def export_pdf(self):
        with db_lock:
            sales = list(daily_sales_db)

        if not sales:
            QMessageBox.information(self, "Aviso", "No hay ventas registradas el día de hoy.")
            return

        try:
            pdf_path = generar_reporte_cierre_dia_pdf(sales)
            if sys.platform == "win32":
                os.startfile(os.path.abspath(pdf_path))
            elif sys.platform == "darwin":
                subprocess.Popen(["open", os.path.abspath(pdf_path)])
            else:
                subprocess.Popen(["xdg-open", os.path.abspath(pdf_path)])
            QMessageBox.information(self, "Reporte Generado", f"Reporte de cierre guardado y abierto:\n{pdf_path}")
        except Exception as e:
            QMessageBox.warning(self, "Error", f"No se pudo generar el reporte PDF: {e}")

    def reset_day(self):
        global last_day_reset_ts
        with db_lock:
            sales_count = len(daily_sales_db)

        if sales_count == 0:
            QMessageBox.information(self, "Aviso", "La caja ya está vacía y lista para un nuevo día.")
            return

        reply = QMessageBox.question(
            self,
            "Confirmar Cierre de Día",
            "¿Deseas cerrar el día actual y reiniciar los totales a cero?\n\n(Se generará automáticamente un respaldo en PDF del cierre antes de reiniciar).",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No
        )

        if reply == QMessageBox.StandardButton.Yes:
            with db_lock:
                try:
                    generar_reporte_cierre_dia_pdf(list(daily_sales_db))
                except Exception:
                    pass
                daily_sales_db.clear()
                last_day_reset_ts = time.time()
                mark_database_changed()

            QMessageBox.information(self, "Día Cerrado", "✅ ¡Día finalizado con éxito! La caja está lista para el próximo turno.")
            self.accept()


# ============================================================
# DIÁLOGO DE GESTIÓN DE MENÚ / PRODUCTOS (PyQt6)
# ============================================================

class MenuManagementDialog(QDialog):

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Gestión de Menú y Productos - RestaurantePOS")
        self.resize(820, 560)

        self.filtered_products = []
        self.local_ip = get_local_ip()
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
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(["ID", "Foto", "Categoría", "Nombre del Producto", "Precio ($)"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
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

            # Icono o estado de foto
            has_photo = bool(prod.get("imageUri"))
            item_photo = QTableWidgetItem("📷 Sí" if has_photo else "—")
            item_photo.setTextAlignment(Qt.AlignmentFlag.AlignCenter)

            item_cat = QTableWidgetItem(prod.get("category", "General"))
            item_name = QTableWidgetItem(prod.get("name", ""))
            item_price = QTableWidgetItem(f"${prod.get('price', 0.0):.2f}")
            item_price.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)

            self.table.setItem(row, 0, item_id)
            self.table.setItem(row, 1, item_photo)
            self.table.setItem(row, 2, item_cat)
            self.table.setItem(row, 3, item_name)
            self.table.setItem(row, 4, item_price)

    def dialog_add_product(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Añadir Nuevo Producto al Menú")
        dialog.resize(420, 360)
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

        # Selector de foto
        selected_photo_path = [None]
        lbl_photo_status = QLabel("Sin imagen")
        lbl_photo_status.setStyleSheet("color: #64748B; font-size: 12px;")

        photo_layout = QHBoxLayout()
        btn_pick_photo = QPushButton("📁 Seleccionar Foto...")
        btn_clear_photo = QPushButton("❌ Quitar")

        photo_layout.addWidget(btn_pick_photo)
        photo_layout.addWidget(btn_clear_photo)

        def pick_photo():
            file_path, _ = QFileDialog.getOpenFileName(
                dialog,
                "Seleccionar Imagen del Producto",
                "",
                "Imágenes (*.png *.jpg *.jpeg *.webp *.bmp)"
            )
            if file_path:
                selected_photo_path[0] = file_path
                lbl_photo_status.setText(f"✓ {os.path.basename(file_path)}")
                lbl_photo_status.setStyleSheet("color: #16A34A; font-weight: bold; font-size: 12px;")

        def clear_photo():
            selected_photo_path[0] = None
            lbl_photo_status.setText("Sin imagen")
            lbl_photo_status.setStyleSheet("color: #64748B; font-size: 12px;")

        btn_pick_photo.clicked.connect(pick_photo)
        btn_clear_photo.clicked.connect(clear_photo)

        form.addRow("Categoría:", cb_cat)
        form.addRow("Nombre del Producto:", txt_name)
        form.addRow("Precio ($):", txt_price)
        form.addRow("Foto del Producto:", photo_layout)
        form.addRow("", lbl_photo_status)

        btn_save = QPushButton("Guardar Producto")
        btn_save.setStyleSheet("background-color: #16A34A; color: white; font-weight: bold; padding: 10px;")

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
                image_uri = ""

                if selected_photo_path[0]:
                    ext = os.path.splitext(selected_photo_path[0])[1] or ".jpg"
                    dest_name = f"prod_{new_id}_{int(datetime.now().timestamp())}{ext}"
                    dest_file = os.path.join(CARPETA_IMAGENES, dest_name)
                    try:
                        shutil.copyfile(selected_photo_path[0], dest_file)
                        image_uri = f"http://{self.local_ip}:{SERVER_PORT}/images/{dest_name}"
                    except Exception as e:
                        print(f"Error copiando imagen: {e}")

                products_db.append({
                    "id": new_id,
                    "category": cat,
                    "name": name,
                    "price": price,
                    "imageUri": image_uri
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
        dialog.resize(420, 360)
        form = QFormLayout()

        cb_cat = QComboBox()
        cb_cat.setEditable(True)
        with db_lock:
            cats = sorted(list(set(p["category"] for p in products_db if p.get("category"))))
        cb_cat.addItems(cats or ["General"])
        cb_cat.setCurrentText(prod.get("category", "General"))

        txt_name = QLineEdit(prod.get("name", ""))
        txt_price = QLineEdit(f"{prod.get('price', 0.0):.2f}")

        selected_photo_path = [None]
        current_uri = prod.get("imageUri", "")
        lbl_photo_status = QLabel(f"✓ Imagen existente" if current_uri else "Sin imagen")
        if current_uri:
            lbl_photo_status.setStyleSheet("color: #16A34A; font-weight: bold; font-size: 12px;")
        else:
            lbl_photo_status.setStyleSheet("color: #64748B; font-size: 12px;")

        photo_layout = QHBoxLayout()
        btn_pick_photo = QPushButton("📁 Cambiar Foto...")
        btn_clear_photo = QPushButton("❌ Quitar Foto")

        photo_layout.addWidget(btn_pick_photo)
        photo_layout.addWidget(btn_clear_photo)

        def pick_photo():
            file_path, _ = QFileDialog.getOpenFileName(
                dialog,
                "Seleccionar Imagen del Producto",
                "",
                "Imágenes (*.png *.jpg *.jpeg *.webp *.bmp)"
            )
            if file_path:
                selected_photo_path[0] = file_path
                lbl_photo_status.setText(f"✓ {os.path.basename(file_path)}")
                lbl_photo_status.setStyleSheet("color: #16A34A; font-weight: bold; font-size: 12px;")

        def clear_photo():
            selected_photo_path[0] = "CLEAR"
            lbl_photo_status.setText("Foto eliminada (se borrará al guardar)")
            lbl_photo_status.setStyleSheet("color: #DC2626; font-size: 12px;")

        btn_pick_photo.clicked.connect(pick_photo)
        btn_clear_photo.clicked.connect(clear_photo)

        form.addRow("Categoría:", cb_cat)
        form.addRow("Nombre del Producto:", txt_name)
        form.addRow("Precio ($):", txt_price)
        form.addRow("Foto del Producto:", photo_layout)
        form.addRow("", lbl_photo_status)

        btn_save = QPushButton("Actualizar Producto")
        btn_save.setStyleSheet("background-color: #2563EB; color: white; font-weight: bold; padding: 10px;")

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

                        if selected_photo_path[0] == "CLEAR":
                            p["imageUri"] = ""
                        elif selected_photo_path[0]:
                            ext = os.path.splitext(selected_photo_path[0])[1] or ".jpg"
                            dest_name = f"prod_{prod['id']}_{int(datetime.now().timestamp())}{ext}"
                            dest_file = os.path.join(CARPETA_IMAGENES, dest_name)
                            try:
                                shutil.copyfile(selected_photo_path[0], dest_file)
                                p["imageUri"] = f"http://{self.local_ip}:{SERVER_PORT}/images/{dest_name}"
                            except Exception as e:
                                print(f"Error copiando imagen: {e}")
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
            f"restaurantSSS - Central de Caja ({APP_VERSION}) | IP: {self.local_ip}:{SERVER_PORT}"
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

        self.refresh_ui()

    # --------------------------------------------------------
    # CONSTRUCCIÓN DE UI
    # --------------------------------------------------------

    def init_ui(self):
        root_layout = QVBoxLayout()
        root_layout.setContentsMargins(12, 12, 12, 8)
        root_layout.setSpacing(10)

        # Banner superior de accesos rápidos
        banner = QFrame()
        banner.setStyleSheet(
            "background-color: #1E293B; color: #F8FAFC; border-radius: 8px; padding: 6px 12px;"
        )
        banner_layout = QHBoxLayout(banner)
        banner_layout.setContentsMargins(8, 4, 8, 4)

        lbl_app_title = QLabel("<h3><b>🍽️ Sistema de Restaurante & Caja</b></h3>")
        lbl_app_title.setStyleSheet("color: white;")

        btn_cierre = QPushButton("📊 Cierre de Día")
        btn_cierre.setStyleSheet(
            "background-color: #0284C7; color: white; font-weight: bold; "
            "padding: 5px 12px; border-radius: 4px; font-size: 12px;"
        )
        btn_cierre.clicked.connect(self.open_cierre_dia)

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

        btn_tax = QPushButton("⚙️ IVA (%)")
        btn_tax.setStyleSheet(
            "background-color: #334155; color: white; border: 1px solid #475569; "
            "padding: 5px 10px; border-radius: 4px; font-size: 12px;"
        )
        btn_tax.clicked.connect(self.config_tax_dialog)

        btn_about = QPushButton("ℹ️ Info")
        btn_about.setStyleSheet(
            "background-color: #334155; color: white; border: 1px solid #475569; "
            "padding: 5px 10px; border-radius: 4px; font-size: 12px;"
        )
        btn_about.clicked.connect(self.show_about_dialog)

        banner_layout.addWidget(lbl_app_title)
        banner_layout.addStretch()
        banner_layout.addWidget(btn_cierre)
        banner_layout.addWidget(btn_menu)
        banner_layout.addWidget(btn_open_folder)
        banner_layout.addWidget(btn_tax)
        banner_layout.addWidget(btn_about)

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
        )
        self.tab_widget.currentChanged.connect(self.on_tab_changed)
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
        # PANEL DERECHO: DETALLE DE COMANDA ACTUAL
        # ====================================================

        right_panel = QVBoxLayout()
        right_panel.setSpacing(8)

        self.lbl_selected_table = QLabel("<h2><b>Selecciona una Mesa</b></h2>")
        self.lbl_selected_table.setStyleSheet("color: #1E293B;")
        right_panel.addWidget(self.lbl_selected_table)

        self.lbl_waiter_info = QLabel("Camarero: -")
        self.lbl_waiter_info.setStyleSheet("color: #64748B; font-size: 13px;")
        right_panel.addWidget(self.lbl_waiter_info)

        # Tabla de Items de la comanda
        self.table_items = QTableWidget()
        self.table_items.setColumnCount(5)
        self.table_items.setHorizontalHeaderLabels(["Cant.", "Producto", "P. Unit.", "Subtotal", "Ajustar"])
        self.table_items.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.table_items.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table_items.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table_items.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table_items.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        self.table_items.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table_items.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table_items.setAlternatingRowColors(True)
        right_panel.addWidget(self.table_items)

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

        btn_actions_layout = QHBoxLayout()
        btn_actions_layout.setSpacing(8)

        btn_cancel_order = QPushButton("🚫 Cancelar Pedido")
        btn_cancel_order.setStyleSheet(
            "background-color: #EF4444; color: white; font-weight: bold; "
            "padding: 14px; font-size: 14px; border-radius: 6px;"
        )
        btn_cancel_order.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_cancel_order.clicked.connect(self.cancel_current_order)

        btn_pay = QPushButton("💳 / 💵 COBRAR MESA")
        btn_pay.setStyleSheet(
            "background-color: #16A34A; color: white; font-weight: bold; "
            "padding: 14px; font-size: 16px; border-radius: 6px;"
        )
        btn_pay.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_pay.clicked.connect(self.open_payment_dialog)

        btn_actions_layout.addWidget(btn_cancel_order, stretch=1)
        btn_actions_layout.addWidget(btn_pay, stretch=2)

        right_panel.addLayout(btn_actions_layout)

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

    def on_tab_changed(self, index: int):
        with db_lock:
            area_ids = list(rooms_db.keys())
            if 0 <= index < len(area_ids):
                self.selected_area_id = area_ids[index]
                self.selected_table_key = None
        self.refresh_ui()

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

                        is_selected = (area_id == self.selected_area_id and name == self.selected_table_key)
                        border_style = "border: 3px solid #38BDF8; font-weight: bold;" if is_selected else "border: 1px solid transparent;"

                        if is_occ:
                            btn.setText(f"{display_label}\n${tot:.2f}")
                            btn.setStyleSheet(
                                f"background-color: #DC2626; color: white; font-weight: bold; "
                                f"font-size: 13px; border-radius: 8px; {border_style}"
                            )
                        else:
                            btn.setText(f"{display_label}\nLibre")
                            btn.setStyleSheet(
                                f"background-color: #16A34A; color: white; font-weight: bold; "
                                f"font-size: 13px; border-radius: 8px; {border_style}"
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
            self.lbl_selected_table.setText(f"<h2><b>{display_header_name} ({room_name})</b></h2>")
            self.lbl_waiter_info.setText(f"Camarero / Atiende: <b>{camarero_txt}</b>")

            items = data.get("items", [])
            for row in range(self.table_items.rowCount()):
                self.table_items.removeCellWidget(row, 4)
            self.table_items.setRowCount(len(items))
            for row_idx, item in enumerate(items):
                cant = item.get("cantidad", 1)
                precio = item.get("precio", 0.0)
                subt = cant * precio

                item_cant = QTableWidgetItem(str(cant))
                item_cant.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
                self.table_items.setItem(row_idx, 0, item_cant)

                self.table_items.setItem(row_idx, 1, QTableWidgetItem(str(item.get("nombre", ""))))

                item_precio = QTableWidgetItem(f"${precio:.2f}")
                item_precio.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
                self.table_items.setItem(row_idx, 2, item_precio)

                item_subt = QTableWidgetItem(f"${subt:.2f}")
                item_subt.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
                self.table_items.setItem(row_idx, 3, item_subt)
                self.table_items.setCellWidget(row_idx, 4, self.build_qty_widget(item))

            subtotal_base = data.get("total", 0.0)
            monto_iva = subtotal_base * (IVA_PERCENTAGE / 100.0)
            total_final = subtotal_base + monto_iva

            self.lbl_subtotal.setText(f"Subtotal: ${subtotal_base:.2f}")
            self.lbl_tax_info.setText(f"IVA ({IVA_PERCENTAGE:.1f}%): ${monto_iva:.2f}")
            self.lbl_total.setText(f"Total a Pagar: ${total_final:.2f}")
        else:
            self.lbl_selected_table.setText("<h2><b>Selecciona una Mesa</b></h2>")
            self.lbl_waiter_info.setText("Camarero: -")
            self.table_items.setRowCount(0)
            self.lbl_subtotal.setText("Subtotal: $0.00")
            self.lbl_tax_info.setText(f"IVA ({IVA_PERCENTAGE:.1f}%): $0.00")
            self.lbl_total.setText("Total a Pagar: $0.00")

    # --------------------------------------------------------
    # AJUSTAR CANTIDADES DE LA COMANDA (BOTONES + / −)
    # --------------------------------------------------------

    def build_qty_widget(self, item: dict) -> QWidget:
        widget = QWidget()
        lay = QHBoxLayout(widget)
        lay.setContentsMargins(4, 2, 4, 2)
        lay.setSpacing(4)

        btn_minus = QPushButton("−")
        lbl_qty = QLabel(str(item.get("cantidad", 1)))
        lbl_qty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lbl_qty.setMinimumWidth(26)
        lbl_qty.setStyleSheet("font-weight: bold; font-size: 13px;")
        btn_plus = QPushButton("+")

        for b in (btn_minus, btn_plus):
            b.setFixedSize(28, 26)
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.setStyleSheet(
                "font-weight: bold; font-size: 15px; color: white; "
                "border-radius: 4px; border: none; padding: 0px;"
            )
        btn_minus.setStyleSheet(btn_minus.styleSheet() + "background-color: #EF4444;")
        btn_plus.setStyleSheet(btn_plus.styleSheet() + "background-color: #16A34A;")

        btn_minus.clicked.connect(lambda: self.adjust_item_quantity(item, -1))
        btn_plus.clicked.connect(lambda: self.adjust_item_quantity(item, +1))

        lay.addStretch()
        lay.addWidget(btn_minus)
        lay.addWidget(lbl_qty)
        lay.addWidget(btn_plus)
        lay.addStretch()
        return widget

    def adjust_item_quantity(self, item: dict, delta: int):
        if not self.selected_area_id or not self.selected_table_key:
            return

        with db_lock:
            room = rooms_db.get(self.selected_area_id)
            if not room:
                return
            t_data = room["mesas"].get(self.selected_table_key)
            if not t_data:
                return

            items = t_data.get("items", [])
            precio_item = item.get("precio", 0.0)
            for it in items:
                if (
                    it.get("nombre") == item.get("nombre")
                    and abs((it.get("precio", 0.0)) - precio_item) < 0.01
                ):
                    nueva_cant = (it.get("cantidad", 1) or 1) + delta
                    if nueva_cant <= 0:
                        items.remove(it)
                    else:
                        it["cantidad"] = int(nueva_cant)
                    update_occupied_status(t_data)
                    mark_database_changed()
                    break

        self.refresh_ui()

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
        dialog.setWindowTitle(f"Mover Comanda de {self.selected_table_key}")
        dialog.resize(320, 200)

        layout = QVBoxLayout()
        layout.setSpacing(10)

        layout.addWidget(QLabel("<b>Selecciona la sala y mesa de destino:</b>"))

        combo_salas = QComboBox()
        combo_targets = QComboBox()
        valid_targets = []

        with db_lock:
            for a_id, r_data in rooms_db.items():
                combo_salas.addItem(r_data.get("name", "Sala"), a_id)

        def repopulate_tables():
            combo_targets.clear()
            valid_targets.clear()
            a_id = combo_salas.currentData()
            if a_id is None:
                return
            with db_lock:
                r_data = rooms_db.get(a_id)
                if not r_data:
                    return
                pfx = r_data.get("prefix", "M").strip()
                for num in range(1, r_data.get("count", 10) + 1):
                    t_k = f"Mesa {num}"
                    t_val = r_data["mesas"].get(t_k, {"items": []})
                    if len(t_val.get("items", [])) == 0:
                        disp = f"{pfx}{num}" if (pfx and pfx.upper() != "M") else f"Mesa {num}"
                        combo_targets.addItem(disp)
                        valid_targets.append((a_id, t_k))

        combo_salas.currentIndexChanged.connect(repopulate_tables)

        layout.addWidget(QLabel("Sala de destino:"))
        layout.addWidget(combo_salas)
        layout.addWidget(QLabel("Mesa de destino:"))
        layout.addWidget(combo_targets)

        repopulate_tables()

        if not valid_targets:
            QMessageBox.information(self, "Aviso", "No hay mesas libres disponibles para mover.")
            return

        btn_confirm = QPushButton("Confirmar Traslado")
        btn_confirm.setStyleSheet("background-color: #3B82F6; color: white; font-weight: bold; padding: 10px;")
        btn_confirm.clicked.connect(lambda: self.execute_move(valid_targets[combo_targets.currentIndex()], dialog))
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
            normalize_rooms_db()
            mark_database_changed()

        dialog.accept()
        self.rebuild_tabs()

        with db_lock:
            area_ids = list(rooms_db.keys())
            if target_area_id in area_ids:
                tab_idx = area_ids.index(target_area_id)
                self.tab_widget.setCurrentIndex(tab_idx)

        self.select_table(target_area_id, target_table_key)
        self.refresh_ui()

    # --------------------------------------------------------
    # COBRO Y FACTURACIÓN
    # --------------------------------------------------------

    def cancel_current_order(self):
        with db_lock:
            if not self.selected_area_id:
                curr_idx = self.tab_widget.currentIndex()
                area_ids = list(rooms_db.keys())
                if 0 <= curr_idx < len(area_ids):
                    self.selected_area_id = area_ids[curr_idx]

            if not self.selected_area_id or not self.selected_table_key:
                QMessageBox.information(self, "Aviso", "Primero haz clic en la mesa ocupada que deseas cancelar.")
                return

            room = rooms_db.get(self.selected_area_id)
            if not room:
                QMessageBox.warning(self, "Error", "No se encontró la sala seleccionada.")
                return

            t_data = room["mesas"].get(self.selected_table_key)
            if not t_data or len(t_data.get("items", [])) == 0:
                QMessageBox.information(self, "Aviso", "La mesa seleccionada no tiene ningún pedido activo.")
                return

            prefix = room.get("prefix", "M").strip()
            num = t_data.get("number", 1)
            display_name = f"{prefix}{num}" if (prefix and prefix.upper() != "M") else f"Mesa {num}"
            room_name = room.get("name", "Sala")

        reply = QMessageBox.question(
            self,
            "Confirmar Cancelación",
            f"¿Estás seguro de que deseas cancelar y vaciar el pedido de {display_name} ({room_name})?\n\nEsta acción liberará la mesa en la PC y en los teléfonos móviles.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No
        )

        if reply == QMessageBox.StandardButton.Yes:
            with db_lock:
                room = rooms_db.get(self.selected_area_id)
                if room and self.selected_table_key in room["mesas"]:
                    room["mesas"][self.selected_table_key]["items"] = []
                    room["mesas"][self.selected_table_key]["camarero"] = ""
                    room["mesas"][self.selected_table_key]["total"] = 0.0
                    normalize_rooms_db()
                    mark_database_changed()

            self.refresh_ui()
            QMessageBox.information(self, "Pedido Cancelado", f"El pedido de {display_name} ha sido cancelado con éxito.")

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

        dialog = QDialog(self)
        dialog.setWindowTitle(f"Cobrar {display_name} ({room['name']})")
        dialog.resize(390, 360)

        layout = QVBoxLayout()
        layout.setSpacing(10)

        # Configuración de IVA dinámica en el cobro
        iva_layout = QHBoxLayout()
        iva_layout.addWidget(QLabel("<b>IVA (%):</b>"))
        txt_iva = QLineEdit(f"{IVA_PERCENTAGE:.1f}")
        txt_iva.setFixedWidth(70)
        iva_layout.addWidget(txt_iva)
        iva_layout.addStretch()
        layout.addLayout(iva_layout)

        lbl_summary = QLabel()
        lbl_summary.setStyleSheet("font-size: 14px; background: #F1F5F9; padding: 10px; border-radius: 6px; border: 1px solid #CBD5E1;")
        layout.addWidget(lbl_summary)

        layout.addWidget(QLabel("<b>Monto en Efectivo ($):</b>"))
        txt_cash = QLineEdit("0.00")
        layout.addWidget(txt_cash)

        layout.addWidget(QLabel("<b>Monto en Tarjeta ($):</b>"))
        txt_card = QLineEdit("0.00")
        layout.addWidget(txt_card)

        lbl_change = QLabel("Cambio / Vuelto: $0.00")
        lbl_change.setStyleSheet("font-size: 15px; font-weight: bold; color: #16A34A;")
        layout.addWidget(lbl_change)

        calculated = {"monto_iva": 0.0, "total_due": subtotal_base}

        def recalculate():
            global IVA_PERCENTAGE
            try:
                pct = float(txt_iva.text()) if txt_iva.text() else 0.0
            except ValueError:
                pct = 0.0

            IVA_PERCENTAGE = pct
            monto_iva = subtotal_base * (pct / 100.0)
            total_due = subtotal_base + monto_iva
            calculated["monto_iva"] = monto_iva
            calculated["total_due"] = total_due

            lbl_summary.setText(
                f"<b>Subtotal:</b> ${subtotal_base:.2f} | <b>IVA ({pct:.1f}%):</b> ${monto_iva:.2f}<br>"
                f"<h3><font color='#16A34A'><b>TOTAL A PAGAR: ${total_due:.2f}</b></font></h3>"
            )

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

        txt_iva.textChanged.connect(recalculate)
        txt_cash.textChanged.connect(recalculate)
        txt_card.textChanged.connect(recalculate)

        recalculate()
        txt_card.setText(f"{calculated['total_due']:.2f}")

        btn_finish = QPushButton("🖨️ Confirmar Pago y Generar Recibo PDF")
        btn_finish.setStyleSheet(
            "background-color: #16A34A; color: white; font-weight: bold; padding: 12px; font-size: 14px; border-radius: 6px;"
        )
        btn_finish.clicked.connect(
            lambda: self.process_payment(
                dialog, txt_cash, txt_card, mesa_data, subtotal_base,
                calculated["monto_iva"], calculated["total_due"], room["name"], display_name
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

        # 1. Guardar registro de venta para el Cierre de Día
        sale_record = {
            "id": int(time.time() * 1000),
            "fecha_hora": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "mesa": display_name,
            "sala": room_name,
            "camarero": mesa_data.get("camarero", "") or "Caja",
            "items": copy.deepcopy(mesa_data.get("items", [])),
            "subtotal": float(subtotal),
            "iva": float(monto_iva),
            "total": float(total_due),
            "efectivo": float(cash),
            "tarjeta": float(card),
            "cambio": float(max(0.0, change))
        }

        # 2. Generar recibo en PDF en segundo plano
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

        # 3. Vaciar mesa y sincronizar al instante (0ms)
        with db_lock:
            daily_sales_db.append(sale_record)
            mesa_data["items"] = []
            mesa_data["camarero"] = ""
            mesa_data["total"] = 0.0
            normalize_rooms_db()
            mark_database_changed()
            self.local_orders_tracker = last_sync_version

        # 4. Cerrar diálogo y refrescar la interfaz de inmediato
        dialog.accept()
        self.refresh_ui()

        # 5. Notificación sonora suave
        try:
            winsound.MessageBeep(winsound.MB_OK)
        except Exception:
            pass

    # --------------------------------------------------------
    # ACCIONES ADICIONALES
    # --------------------------------------------------------

    def open_cierre_dia(self):
        dialog = CierreDiaDialog(self)
        dialog.exec()

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
            f"<h2>restaurantSSS - Central de Caja</h2>"
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
