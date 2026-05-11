"""One-off script to sync keys across the three messages*.properties files.

Reasoning: messages.properties (default), messages_de.properties (DE locale)
and messages_en.properties (EN locale) had drifted: 46 keys present in DE/EN
but not in default, and 76 keys present in default but not in DE/EN. This
script appends the missing keys to each file with appropriate values.

This script is idempotent: it only adds missing keys; it never modifies
existing entries.
"""

import os
import sys


def load_props_full(path):
    lines = []
    keymap = {}
    with open(path, 'r', encoding='utf-8') as f:
        for raw in f:
            line = raw.rstrip('\r\n')
            lines.append(raw)
            if line and not line.startswith('#') and '=' in line:
                k = line.split('=', 1)[0]
                keymap[k] = line
    return lines, keymap


EN_TRANS = {
    'admin.material.job_order': 'Job order',
    'hangar.delete_all.btn': 'Delete all ships',
    'hangar.delete_all.description': 'Removes all of your ships from the hangar. This action cannot be undone.',
    'hangar.delete_all.error.failed': 'Failed to clear hangar. Please try again.',
    'hangar.delete_all.modal.body': 'Really delete all of your ships permanently? This action cannot be undone.',
    'hangar.delete_all.modal.confirm': 'Yes, delete all',
    'hangar.delete_all.modal.title': 'Clear hangar',
    'hangar.delete_all.section_title': 'Clear hangar',
    'hangar.delete_all.success': 'All ships deleted successfully.',
    'hangar.import.btn': 'Import',
    'hangar.import.description': 'Import your ships from a CCU Game Fleetview JSON export file into your hangar.',
    'hangar.import.error.failed': 'Import failed. Please check the file and try again.',
    'hangar.import.error.nofile': 'Please select a JSON file.',
    'hangar.import.label': 'Fleetview JSON file',
    'hangar.import.result.close': 'Close',
    'hangar.import.result.duplicates': 'Duplicates skipped',
    'hangar.import.result.duplicates_list': 'Skipped duplicates',
    'hangar.import.result.imported': 'Imported',
    'hangar.import.result.none': 'None',
    'hangar.import.result.skipped': 'Not recognised',
    'hangar.import.result.skipped_list': 'Unrecognised ships',
    'hangar.import.result.title': 'Import result',
    'hangar.import.title': 'Fleetview import',
    'hangar.import.uploading': 'Uploading\\u2026',
    'inventory.actions': 'Actions',
    'inventory.avgQuality': '\\u00d8 Quality',
    'inventory.bulk.checkout.btn': 'Check out selected',
    'inventory.bulk.checkout.error.conflict': 'Conflict during bulk checkout. Please reload the page.',
    'inventory.bulk.checkout.error.empty': 'Please select at least one entry.',
    'inventory.bulk.checkout.error.failed': 'Bulk checkout failed. Please try again.',
    'inventory.bulk.checkout.error.forbidden': 'No permission for one or more entries.',
    'inventory.bulk.checkout.error.notfound': 'One or more entries were not found.',
    'inventory.bulk.checkout.modal.cancel': 'Cancel',
    'inventory.bulk.checkout.modal.confirm': 'Check out',
    'inventory.bulk.checkout.modal.message': 'Do you really want to fully check out the {0} selected entries? This action cannot be undone.',
    'inventory.bulk.checkout.modal.title': 'Confirm bulk checkout',
    'inventory.bulk.checkout.select.all': 'Select all',
    'inventory.bulk.checkout.success': 'Bulk checkout successful: {0} entries checked out.',
    'inventory.empty': 'You have not stored anything yet.',
    'inventory.maxQuality': 'Max. quality',
    'inventory.totalAmount': 'Total amount',
    'member.join_date': 'Join date',
    'member.join_date.placeholder': 'DD.MM.YYYY',
    'member.join_date.tooltip': 'Date of joining the squadron',
    'member.months_in_squadron': 'Months in squadron',
    'members.delete.title': 'Delete user',
    'members.status.update.error': 'Failed to update status.',
    'members.status.update.failed': 'Status could not be updated.',
    'members.status.updated': 'Status updated successfully.',
    'orders.detail.inventory.unlink.error': 'Failed to unlink inventory entry.',
    'orders.detail.inventory.unlink.success': 'Inventory entry unlinked successfully.',
    'orders.detail.inventory.unlink.tooltip': 'Unlink inventory entry from job order',
    'orders.detail.material.unlink.error': 'Failed to remove the material.',
    'orders.detail.material.unlink.success': 'Material removed from job order successfully.',
    'orders.detail.material.unlink.tooltip': 'Remove link',
    'orders.detail.materialCollection': 'Material collection overview',
    'orders.handover.amount': 'Amount',
    'orders.handover.inventoryItem': 'Inventory entry',
    'orders.handover.remainingAmount': 'still required',
    'orders.handover.remove': 'Remove',
    'orders.handover.report.validation.amount': 'The amount entered for "{0}" exceeds the available stock ({1}).',
    'orders.handover.report.validation.date': 'Please fill in the handover date.',
    'orders.handover.report.validation.handle': 'Please fill in the recipient handle.',
    'orders.handover.report.validation.items': 'Please add at least one material/inventory entry.',
    'orders.handover.report.validation.time': 'Please fill in the handover time.',
    'orders.handover.report.validation.title': 'Required fields are missing',
    'orders.handover.selectInventory': '-- Select inventory entry --',
    'orders.status.update.conflict': 'Conflict: the data has been changed in the meantime. Please reload the page.',
    'orders.status.update.error': 'Could not update status.',
    'orders.status.update.success': 'Status updated successfully.',
    'orders.status.warning.body': 'If you set the status to Completed or Rejected, all linked inventory entries will be detached from the job order. This action cannot be undone.',
    'orders.status.warning.cancel': 'Cancel',
    'orders.status.warning.confirm': 'Confirm',
    'orders.status.warning.title': 'CONFIRM STATUS CHANGE',
    'toast.title.error': 'Error',
    'toast.title.success': 'Success',
}


def append_block(path, lines_existing, additions, header):
    last_line = lines_existing[-1] if lines_existing else ''
    has_trailing_newline = last_line.endswith('\n') or last_line.endswith('\r\n')
    with open(path, 'a', encoding='utf-8', newline='') as f:
        if not has_trailing_newline and lines_existing:
            f.write('\r\n')
        f.write('\r\n')
        f.write(f'# {header}\r\n')
        for line in additions:
            f.write(line + '\r\n')
    print(f"  appended {len(additions)} lines to {path}")


def main():
    root = 'frontend/src/main/resources'
    default_lines, default_keys = load_props_full(f'{root}/messages.properties')
    de_lines, de_keys = load_props_full(f'{root}/messages_de.properties')
    en_lines, en_keys = load_props_full(f'{root}/messages_en.properties')

    need_default = sorted([k for k in de_keys if k not in default_keys])
    need_de = sorted([k for k in default_keys if k not in de_keys])

    missing_trans = [k for k in need_de if k not in EN_TRANS]
    if missing_trans:
        print(f"ERROR: missing translations for {len(missing_trans)} keys:", file=sys.stderr)
        for k in missing_trans[:20]:
            print(f"  {k}", file=sys.stderr)
        sys.exit(1)

    print(f"Adding {len(need_default)} keys to messages.properties (from DE)")
    print(f"Adding {len(need_de)} keys to messages_de.properties (from default)")
    print(f"Adding {len(need_de)} keys to messages_en.properties (translated)")

    default_additions = [de_keys[k] for k in need_default]
    append_block(f'{root}/messages.properties', default_lines, default_additions,
                 'Backfilled from messages_de.properties for full default coverage (key sync 2026-05-11)')

    de_additions = [default_keys[k] for k in need_de]
    append_block(f'{root}/messages_de.properties', de_lines, de_additions,
                 'Backfilled from messages.properties to match default key set (key sync 2026-05-11)')

    en_additions = [f'{k}={EN_TRANS[k]}' for k in need_de]
    append_block(f'{root}/messages_en.properties', en_lines, en_additions,
                 'Backfilled with English translations of messages.properties (key sync 2026-05-11)')

    print("Done.")


if __name__ == '__main__':
    main()
