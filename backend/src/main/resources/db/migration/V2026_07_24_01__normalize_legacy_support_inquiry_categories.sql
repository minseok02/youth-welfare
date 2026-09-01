UPDATE support_inquiries
   SET category = 'ETC',
       updated_at = CURRENT_TIMESTAMP
 WHERE category = 'GENERAL_FEEDBACK';
