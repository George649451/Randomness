"""
Создаёт Google Form «Baraban log form» с десятью текстовыми вопросами
и печатает готовый блок FORM = {...} для вставки в fortune-reels.html.

Подготовка (один раз, ~5 минут):
  1. https://console.cloud.google.com → создать проект (любое имя).
  2. APIs & Services → Library → включить «Google Forms API».
  3. APIs & Services → OAuth consent screen → External → заполнить имя,
     e-mail → Save. В Test users добавить свой gmail.
  4. APIs & Services → Credentials → Create credentials → OAuth client ID
     → Application type: Desktop app → Create → Download JSON.
     Сохранить рядом с этим скриптом как client_secret.json.
  5. pip install google-auth-oauthlib google-api-python-client

Запуск:
  python make_form.py

Откроется браузер для входа в Google; после этого скрипт создаст форму
и выведет конфиг. Один шаг остаётся ручным (Forms API этого не умеет):
в форме → Responses → значок таблицы → Select existing spreadsheet
→ «Baraban log».
"""
import json
import os
import sys

from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

SCOPES = ["https://www.googleapis.com/auth/forms.body"]
FIELDS = ["time", "user_id", "user_name",
          "reel1", "reel2", "reel3", "reel4", "reel5", "reel6", "win"]

if not os.path.exists("client_secret.json"):
    sys.exit("Нет client_secret.json рядом со скриптом — см. шаг 4 в шапке файла.")

creds = InstalledAppFlow.from_client_secrets_file("client_secret.json", SCOPES).run_local_server(port=0)
forms = build("forms", "v1", credentials=creds)

form = forms.forms().create(body={"info": {"title": "Baraban log form"}}).execute()
form_id = form["formId"]

requests = [{
    "createItem": {
        "item": {"title": name, "questionItem": {"question": {"textQuestion": {}}}},
        "location": {"index": i},
    }
} for i, name in enumerate(FIELDS)]
forms.forms().batchUpdate(formId=form_id, body={"requests": requests}).execute()

form = forms.forms().get(formId=form_id).execute()
ids = {}
for item in form["items"]:
    qid = item["questionItem"]["question"]["questionId"]   # hex
    ids[item["title"]] = "entry." + str(int(qid, 16))      # decimal, как в pre-filled link

submit_url = form["responderUri"].replace("/viewform", "/formResponse")

print("\nФорма создана:", "https://docs.google.com/forms/d/%s/edit" % form_id)
print("Не забудьте привязать её к таблице (Responses → значок таблицы).\n")
print("Вставьте в fortune-reels.html вместо const FORM = {...}:\n")
print("const FORM = " + json.dumps({"url": submit_url, "fields": ids}, indent=2, ensure_ascii=False) + ";")
