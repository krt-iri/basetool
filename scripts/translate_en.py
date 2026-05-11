"""Translate likely-German entries in messages_en.properties to English.

Idempotent: only modifies values for keys explicitly listed in TRANSLATIONS.
Keys whose German text was either kept intentionally (proper nouns, codes,
addresses, international terms) are listed in KEEP for clarity but not
touched.
"""

import os
import re

ROOT = 'frontend/src/main/resources'
EN_PATH = f'{ROOT}/messages_en.properties'

# Keys we explicitly KEEP identical to DE (proper nouns, codes, addresses, ints).
KEEP = {
    'admin.job_type.name', 'admin.location.name', 'admin.location.starsystem',
    'admin.material.name', 'admin.materials.quantityType.scu', 'admin.squadron.name',
    'admin.starsystem.name', 'admin.terminal.name', 'admin.terminal.nickname',
    'admin.terminal.system', 'admin.terminals.terminals', 'app.title',
    'general.details', 'general.name',
    'hangar.ship.fitted', 'hangar.ship.insurance.lti', 'hangar.ship.name',
    'hangar.title',
    'impressum.address.city', 'impressum.address.name', 'impressum.address.street',
    'impressum.contact.email_value', 'impressum.credits_end',
    'impressum.credits_generator', 'impressum.credits_lawyer',
    'info.title',
    'lang.de', 'lang.en',
    'material.collection.col.material', 'material.name', 'material.quantity.scu',
    'materials.detail.th.terminal',
    'members.in_keycloak', 'members.status',
    'mission.conflict.section.crew', 'mission.conflict.section.flags',
    'mission.filter.show_past.label',
    'mission.participant.checkin', 'mission.participant.checkout',
    'mission.participant.name', 'mission.participants.count',
    'mission.save.section.crew', 'mission.save.section.flags',
    'mission.status', 'mission.unit.crew', 'mission.unit.frequency.placeholder',
    'mission.unit.hvu', 'mission.unit.name',
    'nav.admin.terminals', 'nav.administration', 'nav.hangar',
    'operation.status',
    'orders.create.scmdb.label',
    'orders.detail.handle', 'orders.detail.status', 'orders.index.handle',
    'orders.index.id', 'orders.index.status',
    'privacy.address.city', 'privacy.address.name', 'privacy.address.street',
    'privacy.contact.email_label', 'privacy.contact.email_value',
    'privacy.p_2_5_3', 'privacy.p_2_5_4', 'privacy.p_2_5_7', 'privacy.p_2_5_8',
    'profit.col.material',
    'refinery.material', 'refinery.status',
    'refineryorder.form.oreSales', 'refineryorder.form.status',
    'refineryorder.index.id', 'refineryorder.index.status',
    'refineryorder.material.input', 'refineryorder.material.output',
    'ship.shiptype', 'ship.manufacturer.name', 'ship.shiptype.name',
}

# Translations to apply: key -> new English value (preserves \uXXXX escapes
# because Spring decodes them at message-bundle load time).
TRANSLATIONS = {
    'admin.terminals.title': 'Manage Terminals',
    'btn.hide': 'Hide',
    'btn.show': 'Show',
    # Errors
    'error.admin.terminals.load': 'Failed to load terminals.',
    'error.concurrency.conflict': 'The data has been changed by another user in the meantime. Please reload the page.',
    'error.delete.jobtype.in_use': 'The job type cannot be deleted because it is still in use.',
    'error.delete.manufacturer.in_use': 'The manufacturer cannot be deleted because it is still used by one or more ship types.',
    'error.delete.shiptype.in_use': 'The ship type cannot be deleted because it is still used by one or more ships.',
    'error.delete.squadron.in_use': 'The squadron cannot be deleted because it is still used by one or more mission participants.',
    'error.duplicate.jobtype': 'A job type with this name already exists.',
    'error.duplicate.location': 'A location with this name already exists.',
    'error.duplicate.manufacturer': 'A manufacturer with this name already exists.',
    'error.duplicate.shiptype': 'A ship type with this name already exists.',
    'error.duplicate.squadron': 'A squadron with this name already exists.',
    'error.duplicate.starsystem': 'A star system with this name already exists.',
    'error.inventory.bookout.failed': 'Failed to check out the entry.',
    'error.inventory.personal.assignment': 'A personal entry must not be assigned to a job order or mission.',
    'error.joborder.assignee.add': 'Failed to add the assignee.',
    'error.joborder.assignee.remove': 'Failed to remove the assignee.',
    'error.materials.matrix.load': 'Failed to load the material matrix.',
    'error.mission.manager.add': 'Failed to add the co-manager.',
    'error.mission.manager.remove': 'Failed to remove the co-manager.',
    'error.mission.manager.user_required': 'Please select a user.',
    'error.mission.owner.change': 'Failed to change the owner.',
    'error.operations.load': 'Failed to load operations.',
    'error.profit_calculation.load': 'Failed to load the initial data for the profit calculation.',
    'error.user.delete': 'Failed to delete the user.',
    # Hangar
    'hangar.ship.manufacturer': 'Manufacturer',
    'hangar.ship.select_manufacturer': 'All manufacturers',
    # Impressum (long German legal text - translated for the English locale only)
    'impressum.liability.content.text': 'The contents of our pages have been created with the utmost care. However, we cannot guarantee the accuracy, completeness or up-to-dateness of the contents. As a service provider we are responsible for our own content on these pages in accordance with the general laws under § 7 para. 1 DDG. According to §§ 8 to 10 DDG, however, we as a service provider are not obliged to monitor transmitted or stored third-party information or to investigate circumstances that indicate illegal activity. Obligations to remove or block the use of information under the general laws remain unaffected. Liability in this respect is, however, only possible from the point in time at which a specific infringement of the law becomes known. Upon becoming aware of corresponding infringements, we will remove this content immediately.',
    'impressum.liability.copyright.text': 'The content and works on these pages created by the site operators are subject to German copyright law. Duplication, processing, distribution and any kind of exploitation outside the limits of copyright require the written consent of the respective author or creator. Downloads and copies of this site are only permitted for private, non-commercial use. Insofar as the content on this site was not created by the operator, third-party copyrights are observed. In particular, third-party content is marked as such. Should you nevertheless become aware of a copyright infringement, please notify us accordingly. Upon becoming aware of legal violations, we will remove such content immediately.',
    'impressum.liability.links.text': 'Our offer includes links to external websites of third parties whose content we have no influence on. Therefore, we cannot accept any liability for these external contents. The respective provider or operator of the pages is always responsible for the content of the linked pages. The linked pages were checked for possible legal violations at the time of linking. Illegal content was not recognisable at the time of linking. However, permanent monitoring of the content of the linked pages is not reasonable without concrete indications of a violation of the law. Upon becoming aware of legal violations, we will remove such links immediately.',
    # Inventory
    'inventory.amount': 'Amount',
    'inventory.bookout.amount': 'Check-out amount',
    'inventory.bookout.amount.of': 'of {0}',
    'inventory.bookout.btn': 'Check out',
    'inventory.bookout.cancel': 'Cancel',
    'inventory.bookout.noTargetUser': '-- Check out --',
    'inventory.bookout.sellAmount': 'Sale amount',
    'inventory.bookout.sellNotPossible': 'Sale not possible',
    'inventory.bookout.submit': 'Check out',
    'inventory.bookout.terminal': 'Terminal',
    'inventory.bookout.title': 'Check out entry',
    'inventory.bookout.type': 'Check-out type',
    'inventory.bookout.type.discard': 'Simple check-out',
    'inventory.bookout.type.sell': 'Sale',
    'inventory.bookout.type.transfer': 'Transfer',
    'inventory.location': 'Location',
    'inventory.material': 'Material',
    'inventory.mission': 'Assign to mission (optional)',
    'inventory.mission.select': '-- No mission --',
    'inventory.order': 'Job order',
    'inventory.overview.aggregated': 'Aggregated squadron inventory',
    'inventory.overview.global': 'Global inventory',
    'inventory.overview.my': 'My inventory',
    'inventory.personal.help': 'This entry is only visible to you and does not appear in the global inventory.',
    'inventory.personal.label': 'Personal entry',
    'inventory.user': 'User',
    # Materials detail
    'materials.detail.noData': 'No price data available.',
    'materials.detail.noResults': 'No terminals found.',
    'materials.detail.th.priceBuy': 'Buy price',
    'materials.detail.th.priceSell': 'Sell price',
    # Members
    'members.delete': 'Delete',
    'members.delete_confirm': 'Do you really want to permanently delete this user? All data will be transferred to an administrator.',
    'members.is_logistician': 'Logistician',
    'members.is_mission_manager': 'Mission manager',
    'members.not_in_keycloak': 'Not in Keycloak',
    # Mission
    'mission.details': 'Details',
    'mission.management_rights': 'Management rights',
    'mission.manager.select': '-- Select manager --',
    'mission.managers': 'Co-managers',
    'mission.name': 'Name',
    'mission.owner': 'Owner',
    'mission.owner.confirm': 'Do you really want to change the owner of this mission?',
    'mission.owner.select': '-- Select new owner --',
    'mission.owner.success': 'Owner changed successfully.',
    # Navigation
    'nav.materials.profit_calculation': 'Profit calculation',
    'nav.operations': 'Operations',
    # Notifications
    'notification.success.mission.manager.add': 'Co-manager added successfully.',
    'notification.success.mission.manager.remove': 'Co-manager removed successfully.',
    # Operation
    'operation.delete.confirm': 'Do you really want to delete this operation? All associated missions will also be deleted. Inventory and refinery orders are preserved but detached.',
    'operation.delete.error': 'Failed to delete the operation.',
    'operation.delete.success': 'Operation deleted successfully.',
    'operation.details': 'Details',
    'operation.name': 'Name',
    'operation.prefix': 'Operation:',
    # Orders / SCMDB
    'orders.create.scmdb.button': 'Extract materials',
    'orders.create.scmdb.error.no_match': 'No known materials found in the export.',
    'orders.create.scmdb.error.not_found': 'The following materials were not found in the system',
    'orders.create.scmdb.error.some_unknown': 'some unknown',
    'orders.create.scmdb.placeholder': 'Paste the text from scmdb.net here...',
    'orders.create.scmdb.success': 'Materials extracted successfully.',
    'orders.detail.assignees.addMe': 'Add myself',
    'orders.detail.assignees.addUser': 'Add',
    'orders.detail.assignees.empty': 'No one is working on this order yet.',
    'orders.detail.assignees.selectUser': '-- Select user --',
    'orders.detail.assignees.title': 'Assignees',
    'orders.detail.status.update': 'Update',
    'orders.handover.selectSquadron': '-- Optional --',
    'orders.index.filter.status': 'Status:',
    'orders.index.prio': 'Prio',
    # Privacy policy
    'privacy.contact.phone': 'Phone: 0170-9677429',
    'privacy.h1_1': '1. Privacy at a glance',
    'privacy.h1_2': '2. General notes and mandatory information',
    'privacy.h1_3': '3. Data collection on this website',
    'privacy.h2_1_1': 'General notes',
    'privacy.h2_1_2': 'Who is responsible for the data collection on this website?',
    'privacy.h2_1_3': 'What do we use your data for?',
    'privacy.h2_1_4': 'What rights do you have regarding your data?',
    'privacy.h2_2_1': 'Controller',
    'privacy.h2_2_2': 'Storage duration',
    'privacy.h2_2_3': 'Your rights as a data subject',
    'privacy.h2_2_4': 'Withdrawal of your consent to data processing',
    'privacy.h2_2_5': 'Right to lodge a complaint with the competent supervisory authority',
    'privacy.h2_3_1': 'External hosting',
    'privacy.h2_3_2': 'Cookies',
    'privacy.h2_3_3': 'Server log files',
    'privacy.h2_3_4': 'Contact form and forum',
    'privacy.last_updated': 'Last updated: 11.04.2026',
    'privacy.list_2_3_1': 'Right of access under Art. 15 GDPR',
    'privacy.list_2_3_2': 'Right to rectification under Art. 16 GDPR',
    'privacy.list_2_3_3': 'Right to erasure (“right to be forgotten”) under Art. 17 GDPR',
    'privacy.list_2_3_4': 'Right to restriction of processing under Art. 18 GDPR',
    'privacy.list_2_3_5': 'Right to data portability under Art. 20 GDPR',
    'privacy.list_2_3_6': 'Right to object under Art. 21 GDPR',
    'privacy.list_2_3_7': 'Right to lodge a complaint with a supervisory authority under Art. 77 GDPR',
    'privacy.p_1_1': 'The following information provides a simple overview of what happens to your personal data when you visit this website. Personal data is any data that can be used to personally identify you.',
    'privacy.p_1_2': 'Data processing on this website is carried out by the website operator named in the legal notice.',
    'privacy.p_1_3': 'Some data is collected to ensure the error-free provision and security of the website (e.g. via server log files and technically necessary cookies). Other data that you actively enter (e.g. in the contact form or forum) is used to handle your enquiries or to provide the community functions.',
    'privacy.p_1_4': 'You have the right at any time to obtain information about the origin, recipient and purpose of your stored personal data. You also have the right to demand rectification, deletion or restriction of processing. You also have the right to object and the right to lodge a complaint with the competent supervisory authority.',
    'privacy.p_2_1': 'The controller responsible for data processing on this website is:',
    'privacy.p_2_2': 'In the event of a deletion request for your user account, all associated personal data (such as e-mail address, IP addresses, etc.) will be permanently removed from our systems. To preserve the integrity and context of discussions in our forum for the remaining community, your posts and comments will be anonymised by default. Your username will be replaced with a neutral placeholder (e.g. "[Former member]") so that no direct conclusions about your person are possible. Your statutory right under Art. 17 GDPR to demand complete deletion of these posts as well remains unaffected. You can express this wish as part of your deletion request. The default anonymisation procedure is based on our legitimate interest (Art. 6 (1) (f) GDPR) in the continued existence of the community content.',
    'privacy.p_2_3': 'Under the applicable statutory provisions (especially the GDPR) you have the following rights:',
    'privacy.p_2_4': 'Many data processing operations are only possible with your express consent. You can revoke any consent you have already given at any time. An informal notification by e-mail to us is sufficient. The lawfulness of the data processing carried out until the revocation remains unaffected by the revocation.',
    'privacy.p_2_5_1': 'In the event of violations of data protection law, you have the right to lodge a complaint with the competent supervisory authority. The supervisory authority responsible for us is:',
    'privacy.p_2_5_2': 'The State Commissioner for Data Protection and Freedom of Information of Rhineland-Palatinate',
    'privacy.p_2_5_5': 'Phone: +49 (0) 6131 8920-0',
    'privacy.p_2_5_6': 'Fax: +49 (0) 6131 8920-299',
    'privacy.p_3_1_1': 'This website is hosted by an external service provider. The personal data collected on this website is stored on the servers of the host. This may primarily include IP addresses, contact requests, meta and communication data, contact data, names and website accesses.',
    'privacy.p_3_1_2': 'The use of the host serves the purpose of fulfilling the contract with our users (Art. 6 (1) (b) GDPR) and is in the interest of secure, fast and efficient provision of our online offering by a professional provider (Art. 6 (1) (f) GDPR).',
    'privacy.p_3_1_3': 'We use the following host:<br>Hetzner Online GmbH<br>Industriestr. 25<br>91710 Gunzenhausen<br>Germany',
    'privacy.p_3_1_4': 'We have concluded a data processing agreement (DPA) with our host in accordance with Art. 28 GDPR.',
    'privacy.p_3_2_1': 'Our internet pages exclusively use technically necessary “cookies”. Cookies are small text files that are stored on your end device. The processing of these cookies is based on Art. 6 (1) (f) GDPR as well as § 25 (2) TDDDG. Our legitimate interest lies in the technically faultless and optimised provision of our services.',
    'privacy.p_3_2_2': '<strong>Session cookie (User-Session)</strong><br>This cookie stores a randomly generated session ID and is required for basic functions such as the login status. It is automatically deleted as soon as you close the browser.',
    'privacy.p_3_2_3': '<strong>XSRF-TOKEN</strong><br>This cookie serves security by preventing cross-site request forgery attacks. It is automatically deleted at the end of the session.',
    'privacy.p_3_3_1': 'The provider of the pages automatically collects and stores information in server log files which your browser transmits. The collection is based on Art. 6 (1) (f) GDPR. Our legitimate interest lies in ensuring the stability and security of our website. To this end, visitors’ IP addresses are stored in unabbreviated form for a maximum of 7 days to enable analysis in the event of an attack. After this period the IP addresses are anonymised by zeroing the last octet of IPv4 addresses and the last 80 bits of IPv6 addresses so that no personal reference can be established.',
    'privacy.p_3_4_1': 'The processing of the data you enter in the contact form or during forum registration is exclusively based on your consent (Art. 6 (1) (a) GDPR).',
    # Profit calculation
    'profit.col.load_cost': 'Load cost',
    'profit.col.margin': 'Margin (%)',
    'profit.col.max_sell': 'Sell (max)',
    'profit.col.min_buy': 'Buy (min)',
    'profit.col.profit_scu': 'Profit / SCU',
    'profit.col.total_profit': 'Max profit',
    'profit.error.fetch': 'Failed to fetch the profit calculation.',
    'profit.filter.ship': 'Select ship',
    'profit.filter.ship.placeholder': '-- Select ship --',
    'profit.filter.systems': 'Restrict systems',
    'profit.filter.systems.placeholder': '-- Add system --',
    'profit.info.autoload': 'Considers only auto-load terminals.',
    'profit.info.hullc': 'Hull C special rule (Loading Dock) active.',
    'profit.info.loading': 'Calculation in progress...',
    'profit.info.no_data': 'No trade data available for the selected filters.',
    'profit.info.select_ship': 'Please select a ship to start the calculation.',
    # Refinery
    'refinery.amount': 'Amount',
    'refinery.ends_at': 'Ends',
    'refinery.location': 'Location',
    'refinery.started_at': 'Started',
    'refinery.status.FINISHED': 'Completed',
    'refinery.status.IN_PROGRESS': 'In progress',
    'refinery.status.OPEN': 'Open',
    'refinery.user': 'User',
    'refineryorder.action.details': 'Details',
    'refineryorder.form.duration.hours': 'h',
    'refineryorder.form.duration.minutes': 'min',
    'refineryorder.form.endsAt': 'Finishes on',
    'refineryorder.form.owner': 'Owner',
    'refineryorder.index.filter.onlyMine': 'My orders',
    'refineryorder.index.owner': 'Owner',
    # Success messages
    'success.inventory.bookout': 'Entry checked out successfully.',
    'success.joborder.assignee.added': 'Assignee added successfully.',
    'success.joborder.assignee.removed': 'Assignee removed successfully.',
    'success.user.delete': 'User deleted successfully.',
    # Unsaved-changes modal
    'unsaved.modal.btn.leave': 'Leave without saving',
    'unsaved.modal.btn.stay': 'Back',
    'unsaved.modal.message': 'You have unsaved changes. Do you really want to leave the page and discard the changes?',
    'unsaved.modal.title': 'Unsaved changes',
}


def main():
    # Read raw lines, replace value for matching keys only.
    with open(EN_PATH, 'r', encoding='utf-8', newline='') as f:
        raw = f.read()
    lines = raw.split('\n')

    changed = 0
    for i, line in enumerate(lines):
        stripped = line.rstrip('\r')
        if not stripped or stripped.startswith('#') or '=' not in stripped:
            continue
        k, _, _ = stripped.partition('=')
        if k in TRANSLATIONS:
            new_val = TRANSLATIONS[k]
            had_cr = line.endswith('\r')
            lines[i] = f'{k}={new_val}' + ('\r' if had_cr else '')
            changed += 1

    new_raw = '\n'.join(lines)
    with open(EN_PATH, 'w', encoding='utf-8', newline='') as f:
        f.write(new_raw)
    print(f"Translated {changed} keys in {EN_PATH}")
    print(f"Defined {len(TRANSLATIONS)} translation entries (some may be no-ops).")
    print(f"KEEP-list size (intentionally identical to DE): {len(KEEP)}")


if __name__ == '__main__':
    main()
