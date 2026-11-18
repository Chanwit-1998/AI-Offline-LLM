import os

def replace_in_files():
    target_dir = "android" # หรือระบุ path ที่ต้องการ
    replacements = {
        "Andriy Druk": "Chanwit",
        "andriydruk": "Chanwit-1998",
        "me@andriydruk.com": "chanwit@developer.com", # ปรับเปลี่ยนอีเมลได้ตามต้องการ
        "LM Playground": "AI-OFFLINE-LLM",
        "lmplayground": "ai_offline_llm"
    }
    
    # นามสกุลไฟล์ที่ต้องการค้นหาและแทนที่
    text_extensions = {".kt", ".kts", ".java", ".xml", ".md", ".json", ".properties", ".gradle"}
    
    count = 0
    for root, dirs, files in os.walk(target_dir):
        # ข้ามโฟลเดอร์ที่ไม่จำเป็น เช่น build, .cxx
        if "build" in dirs:
            dirs.remove("build")
        if ".cxx" in dirs:
            dirs.remove(".cxx")
            
        for file in files:
            ext = os.path.splitext(file)[1]
            if ext in text_extensions or file.lower() == "readme.md":
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, "r", encoding="utf-8") as f:
                        content = f.read()
                    
                    original_content = content
                    for old_text, new_text in replacements.items():
                        # แทนที่แบบตรงๆ และแบบตัวพิมพ์เล็ก
                        content = content.replace(old_text, new_text)
                        content = content.replace(old_text.lower(), new_text.lower())
                        
                    if content != original_content:
                        with open(file_path, "w", encoding="utf-8") as f:
                            f.write(content)
                        print(f"Updated: {file_path}")
                        count += 1
                except Exception as e:
                    pass
                    
    print(f"Replacement completed! Total files updated: {count}")

if __name__ == "__main__":
    replace_in_files()
